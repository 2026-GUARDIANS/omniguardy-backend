package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.VisionAnalysisPort;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob.State.*;

/** No enclosing transaction: all I/O is outside the fenced persistence/preparation transactions. */
@Service @RequiredArgsConstructor @Slf4j
public class ProcessVisionFallbackService {
    private final VisionFallbackRepository jobs;
    private final StoredStreamingVideoReader reader;
    private final VisionAnalysisPort vision;
    private final VisionFallbackEventPort events;
    private final VisionFallbackFailureClassifier failures;
    private final VisionFallbackPolicy policy;
    private final ObjectMapper mapper;

    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void processDue() {
        for(var job:jobs.interrupted(policy.batchSize())) {
            try { jobs.reconcile(job,events.assessed(job.eventId())); }
            catch(RuntimeException failure) { logFailure(job.sessionId(),failure); }
        }
        try { jobs.discover(policy); }
        catch(RuntimeException failure) { logFailure("discovery",failure); return; }
        for(String id:jobs.due(policy.batchSize())) {
            try { jobs.claim(id,policy).ifPresent(this::process); }
            catch(RuntimeException failure) { logFailure(id,failure); }
        }
    }
    private void process(VisionFallbackJob claim) {
        try {
            if(claim.attempts()>policy.maxPreparationAttempts()) { jobs.finish(claim,FAILED,"PREPARATION_RETRY_EXHAUSTED"); return; }
            var source=jobs.source(claim);
            if(events.alreadyOwned(source,claim.eventId())) { jobs.finish(claim,SKIPPED,"EXISTING_EVENT_OWNS_ANALYSIS"); return; }
            if(source.video().isEmpty()) { jobs.waitForVideo(claim,policy); return; }
            if(claim.visionJson()==null && source.video().get().storedAt().isAfter(claim.videoDeadline())) {
                jobs.finish(claim,FAILED,"VIDEO_WAIT_TIMEOUT"); return;
            }
            VisionAnalysis result;
            if(claim.visionJson()!=null) result=mapper.readValue(claim.visionJson(),VisionAnalysis.class);
            else {
                if(!jobs.beginVision(claim,source.video().get(),policy.maxVisionAttempts())) {
                    jobs.finish(claim,FAILED,"VISION_RETRY_EXHAUSTED"); return;
                }
                try(var download=reader.read(source.video().get())) {
                    result=vision.analyze(download.file(),source.context());
                    validate(result,source.context());
                    if(!jobs.analyzed(claim,result)) return;
                }
            }
            validate(result,source.context());
            events.prepare(claim,result,source);
        } catch(Exception failure) {
            jobs.retry(claim,policy,failures.reason(failure),failures.permanent(failure)); return;
        }
        var prepared=jobs.find(claim.sessionId()).orElseThrow();
        if(!Objects.equals(prepared.leaseToken(),claim.leaseToken()) || !jobs.startCalling(prepared,policy)) return;
        var calling=jobs.find(claim.sessionId()).orElseThrow();
        try {
            events.analyze(calling);
            boolean assessed=events.assessed(calling.eventId());
            jobs.finish(calling,assessed ? SUCCEEDED : UNCERTAIN,assessed ? "AGENT_COMPLETED" : "AGENT_OUTCOME_UNKNOWN");
        } catch(RuntimeException failure) {
            // Once the Agent boundary is entered, never automatically repeat Agent/FCM/MQTT.
            jobs.finish(calling,events.assessed(calling.eventId()) ? SUCCEEDED : UNCERTAIN,"AGENT_OUTCOME_REQUIRES_RECONCILIATION");
        }
    }
    private void validate(VisionAnalysis result,VideoTriggerContext context) {
        if(result==null || !Objects.equals(result.triggerId(),context.triggerId()) || result.triggerType()!=context.triggerType()
                || !Objects.equals(result.securityEventId(),context.securityEventId()) || result.triggeredAt()==null
                || !result.triggeredAt().toInstant().equals(java.time.OffsetDateTime.parse(context.triggeredAt()).toInstant())
                || result.observations()==null || result.observations().trackedPersonCount()==null || result.observations().trackedPersonCount()<0)
            throw new VisionFallbackException("INVALID_MP4_RESULT_BINDING_OR_PERSON_COUNT",true);
        boolean valid;
        if(context.triggerType()==TriggerType.KEYPAD) valid=result.behavior()==null;
        else if(result.isExhibitionRules()) valid=result.visionEvents().stream().allMatch(e -> e!=null && e.isExhibitionRulesEvent());
        else valid=(result.analysisMode()==null || result.analysisMode().isBlank() || result.isTemporalTrial6()) && result.behavior()!=null
                    && result.prediction()!=null && Set.of("N1","A17","A18","A19","A21").contains(result.prediction().toUpperCase(Locale.ROOT)) && !result.hasVisionEvents();
        if(!valid || result.visionEvents().stream().anyMatch(Objects::isNull)) throw new VisionFallbackException("INVALID_MP4_RESULT_CONTRACT",true);
    }
    private void logFailure(String id,Throwable failure) { log.warn("Vision fallback failure sessionId={} failureType={}",id,failure.getClass().getSimpleName()); }
}
