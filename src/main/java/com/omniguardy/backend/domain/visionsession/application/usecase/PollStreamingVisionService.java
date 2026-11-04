package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.visionsession.application.model.VisionPollingPolicy;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Clock;
import com.omniguardy.backend.global.performance.PerformanceLog;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollStreamingVisionService {
    private final VisionPollingRepository jobs;
    private final StreamingVisionQueryPort vision;
    private final VisionPollingPolicy policy;
    private final Clock visionStreamingClock;

    public void pollDue() {
        for (String id : jobs.findDue(policy.batchSize())) {
            try { jobs.claim(id, policy.leaseDuration()).ifPresent(this::poll); }
            catch (RuntimeException exception) {
                // Storage/unexpected failures leave the durable lease to expire and be reclaimed.
                log.error("Streaming Vision polling failed sessionId={} failureType={}", id, exception.getClass().getSimpleName());
            }
        }
    }

    private void poll(VisionPollingJob job) {
        try (var scope = PerformanceLog.scope(job.binding().securityEventId(), job.binding().triggerId(),
                job.binding().sessionId(), job.binding().triggerType().name(), job.attempts())) {
            pollSession(job);
        }
    }

    private void pollSession(VisionPollingJob job) {
        if (!visionStreamingClock.instant().isBefore(job.deadlineAt())) {
            fail(job, "TIMEOUT", null); return;
        }
        StreamingVisionStatus status;
        try { status = vision.status(job.binding().sessionId()); }
        catch (StreamingVisionException failure) { upstreamFailure(job, failure, null); return; }
        if (!job.binding().equals(status.binding())) { fail(job, "BINDING_MISMATCH", status.rawJson()); return; }
        if (!visionStreamingClock.instant().isBefore(job.deadlineAt())) { fail(job, "TIMEOUT", status.rawJson()); return; }
        if (status.analysisState() == VisionAnalysisState.FAILED && !status.finalResultAvailable()) {
            jobs.finish(job, null, VisionFinalOutcome.FAILED, "VISION_FAILED", status.error(), status.rawJson()); return;
        }
        boolean terminal = status.analysisState() == VisionAnalysisState.COMPLETED || status.analysisState() == VisionAnalysisState.FAILED;
        if (!terminal || !status.finalResultAvailable()) {
            jobs.reschedule(job, policy.interval(), "ANALYSIS_PENDING", status.error(), status.rawJson()); return;
        }
        if (!status.inputComplete()) { fail(job, "INVALID_STATUS", status.rawJson()); return; }
        try {
            var result = vision.result(job.binding().sessionId());
            if (result.isEmpty()) {
                jobs.reschedule(job, policy.interval(), "RESULT_PENDING", null, status.rawJson()); return;
            }
            var finalResult = result.get();
            var outcome = finalResult.evaluate(job.binding());
            String reason = !job.binding().equals(finalResult.binding()) ? "BINDING_MISMATCH"
                    : switch (outcome) {
                        case VALID -> "VALID_RESULT";
                        case PARTIAL -> "PARTIAL_RESULT";
                        case FAILED -> "VISION_FAILED";
                        case REJECTED -> "INVALID_FINAL_RESULT";
                    };
            // A FAILED status may never turn into an eligible result in the same attempt.
            if (status.analysisState() == VisionAnalysisState.FAILED && outcome == VisionFinalOutcome.VALID) {
                outcome = VisionFinalOutcome.REJECTED; reason = "INCONSISTENT_RESULT";
            }
            jobs.finish(job, finalResult, outcome, reason, finalResult.error(), status.rawJson());
        } catch (StreamingVisionException failure) { upstreamFailure(job, failure, status.rawJson()); }
    }

    private void upstreamFailure(VisionPollingJob job, StreamingVisionException failure, String statusJson) {
        if (failure.retryable()) jobs.reschedule(job, policy.interval(), failure.code(), failure.code(), statusJson);
        else fail(job, failure.code(), statusJson);
    }

    private void fail(VisionPollingJob job, String reason, String statusJson) {
        jobs.finish(job, null, VisionFinalOutcome.FAILED, reason, reason, statusJson);
    }
}
