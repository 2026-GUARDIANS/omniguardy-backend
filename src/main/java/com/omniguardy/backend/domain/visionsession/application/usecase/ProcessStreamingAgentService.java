package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.visionsession.application.model.StreamingAgentPolicy;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingAgentInputException;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.omniguardy.backend.global.performance.PerformanceLog;
import static com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob.State.*;
import static com.omniguardy.backend.domain.visionsession.application.port.out.StreamingAgentEventPort.EventState.ASSESSED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessStreamingAgentService {
    private final StreamingAgentRepository jobs;
    private final StreamingAgentMappingPort mapping;
    private final StreamingAgentEventPort events;
    private final StreamingAgentFailureClassifier failures;
    private final StreamingAgentPolicy policy;

    public void processDue() {
        for (var job : jobs.unresolved(policy.batchSize())) {
            try { jobs.reconcile(job, events.state(job.eventId()) == ASSESSED); }
            catch (RuntimeException failure) { logFailure(job.sessionId(), failure); }
        }
        for (String id : jobs.discover(policy.batchSize())) jobs.enqueue(id);
        for (String id : jobs.due(policy.batchSize())) {
            try { jobs.claim(id, policy.preparationLease()).ifPresent(this::prepare); }
            catch (RuntimeException failure) { logFailure(id, failure); } // Lease recovery handles failed DB writes.
        }
    }

    private void prepare(StreamingAgentJob claim) {
        try (var scope = PerformanceLog.scope(claim.eventId(), null, claim.sessionId(), null, claim.attempts())) {
            prepareAndCall(claim);
        }
    }

    private void prepareAndCall(StreamingAgentJob claim) {
        if (claim.attempts() > policy.maxAttempts()) {
            jobs.finish(claim, FAILED, "RETRY_EXHAUSTED", "NOT_STARTED"); return;
        }
        try {
            var source = jobs.source(claim.sessionId());
            if (source.isEmpty()) { jobs.finish(claim, BLOCKED, "INELIGIBLE_FINAL_RESULT", "NOT_STARTED"); return; }
            var preparation = events.prepare(claim, mapping.map(source.get()));
            if (preparation.action() == StreamingAgentEventPort.Action.STALE) return;
            if (preparation.action() != StreamingAgentEventPort.Action.ASSESS) {
                jobs.finish(claim, SKIPPED, preparation.action().name(), "NOT_STARTED"); return;
            }
        } catch (StreamingAgentInputException input) {
            jobs.finish(claim, BLOCKED, input.getMessage(), "NOT_STARTED"); return;
        } catch (RuntimeException failure) {
            // Preparation has no external effects and its event + ledger link transaction has rolled back.
            if (failures.classify(failure) == StreamingAgentFailureClassifier.Kind.PERMANENT)
                jobs.finish(claim, FAILED, failures.code(failure), "NOT_STARTED");
            else jobs.retry(claim, policy.retryDelay(), policy.maxAttempts(), "PREPARATION_FAILURE");
            return;
        }
        var prepared = jobs.find(claim.sessionId()).orElseThrow();
        if (!claim.leaseToken().equals(prepared.leaseToken()) || !jobs.startCalling(prepared, policy.callingLease())) return;
        var calling = jobs.find(claim.sessionId()).orElseThrow();
        try {
            events.analyze(calling); // Reuses AnalyzeAgentRiskUseCase outside all DB transactions.
            if (events.state(calling.eventId()) == ASSESSED)
                jobs.finish(calling, SUCCEEDED, "AGENT_COMPLETED", "BEST_EFFORT_ATTEMPTED");
            else jobs.finish(calling, UNCERTAIN, "ASSESSMENT_NOT_CONFIRMED", "UNKNOWN_NOT_REPLAYED");
        } catch (RuntimeException failure) {
            var state = events.state(calling.eventId());
            if (state == ASSESSED) {
                jobs.finish(calling, SUCCEEDED, "RECOVERED_ASSESSMENT", "UNKNOWN_NOT_REPLAYED");
            } else if (state == StreamingAgentEventPort.EventState.FAILED) {
                if (failures.classify(failure) == StreamingAgentFailureClassifier.Kind.RETRYABLE)
                    jobs.retry(calling, policy.retryDelay(), policy.maxAttempts(), failures.code(failure));
                else jobs.finish(calling, FAILED, failures.code(failure), "NOT_STARTED");
            } else jobs.finish(calling, UNCERTAIN, "AGENT_OUTCOME_UNKNOWN", "UNKNOWN_NOT_REPLAYED");
        }
    }

    private void logFailure(String id, RuntimeException failure) {
        log.error("Streaming Agent worker failure sessionId={} failureType={}", id, failure.getClass().getSimpleName());
    }
}
