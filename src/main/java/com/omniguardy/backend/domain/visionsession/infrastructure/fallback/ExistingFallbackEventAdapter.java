package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.securityevent.application.port.in.RecordKeypadVisionInputPort;
import com.omniguardy.backend.domain.securityevent.application.usecase.AnalyzeAgentRiskUseCase;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.model.VisionFallbackSource;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Component @RequiredArgsConstructor
public class ExistingFallbackEventAdapter implements VisionFallbackEventPort {
    private final VisionFallbackRepository jobs;
    private final SecurityEventRepository events;
    private final RecordKeypadVisionInputPort keypad;
    private final AnalyzeAgentRiskUseCase agent;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Override public boolean alreadyOwned(VisionFallbackSource source,String linked) {
        if(linked!=null) return !jdbc.queryForList("SELECT event_id FROM security_agent_executions WHERE event_id=?",String.class,linked).isEmpty();
        var context=source.context();
        var rows=jdbc.queryForList("SELECT event_id,status,vision_analyzed_at FROM security_events WHERE trigger_id=? AND trigger_type=?",context.triggerId(),context.triggerType().name());
        for(var row:rows) {
            if(row.get("vision_analyzed_at")!=null || Set.of("VISION_ANALYZED","COMPLETED").contains(row.get("status"))
                    || !jdbc.queryForList("SELECT event_id FROM security_agent_executions WHERE event_id=?",String.class,row.get("event_id")).isEmpty()) return true;
        }
        return false;
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void prepare(VisionFallbackJob claim,VisionAnalysis vision,VisionFallbackSource source) {
        jdbc.queryForList("SELECT session_id FROM vision_fallback_jobs WHERE session_id=? FOR UPDATE",String.class,claim.sessionId());
        if(!jobs.owns(claim)) return;
        if(alreadyOwned(source,claim.eventId())) { jobs.finish(claim,VisionFallbackJob.State.SKIPPED,"EXISTING_EVENT_OWNS_ANALYSIS"); return; }
        String path=source.video().orElseThrow().target().path();
        if(path.length()>500) throw new VisionFallbackException("VIDEO_PATH_TOO_LONG_FOR_EVENT",true);
        String id=source.context().securityEventId();
        if(vision.triggerType()==TriggerType.KEYPAD) {
            if(!vision.hasVisionEvents()) { jobs.prepared(claim,null,false,"NO_EVENTS"); return; }
            id=claim.eventId();
            if(id==null) id=keypad.record(new RecordKeypadVisionInputPort.Command(vision.triggerId(),vision.triggeredAt(),path,
                    vision.prediction(),vision.confidence(),vision.analysisMode(),mapper.writeValueAsString(vision.classProbabilities()),
                    mapper.writeValueAsString(vision.visionEvents()),vision.personCount(),vision.videoDurationSeconds(),vision.analyzedAt()));
        }
        jdbc.queryForList("SELECT event_id FROM security_events WHERE event_id=? FOR UPDATE",String.class,id);
        var event=events.findByEventId(id).orElseThrow(() -> new VisionFallbackException("EVENT_NOT_FOUND",true));
        if(event.getTriggerType()!=EventTriggerType.valueOf(vision.triggerType().name()) || !Objects.equals(vision.triggerId(),event.getTriggerId()))
            throw new VisionFallbackException("EVENT_BINDING_MISMATCH",true);
        if("COMPLETED".equals(event.getStatus())) { jobs.finish(claim,VisionFallbackJob.State.SKIPPED,"EXISTING_EVENT_COMPLETED"); return; }
        if(claim.eventId()==null && vision.triggerType()==TriggerType.AUDIO && (!"CAMERA_REQUESTED".equals(event.getStatus()) || event.getVisionAnalyzedAt()!=null)) {
            jobs.finish(claim,VisionFallbackJob.State.SKIPPED,"EXISTING_EVENT_OWNS_ANALYSIS"); return;
        }
        event.updateVideoPath(path);
        event.updateVisionResult(vision.prediction(),vision.confidence(),vision.analysisMode(),mapper.writeValueAsString(vision.classProbabilities()),
                mapper.writeValueAsString(vision.visionEvents()),vision.personCount(),vision.videoDurationSeconds(),vision.analyzedAt());
        boolean assess=vision.triggerType()==TriggerType.AUDIO ? vision.requiresAudioRiskAssessment() : vision.hasHighRiskKeypadEvent();
        if(!assess && vision.triggerType()==TriggerType.AUDIO) event.completeWithoutRisk();
        events.save(event);
        jobs.prepared(claim,id,assess,assess ? "AGENT_READY" : "NO_RISK");
    }
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void analyze(VisionFallbackJob claim) {
        if(jobs.isCalling(claim)) agent.analyzeReserved(claim.eventId(),claim.leaseToken());
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true)
    public boolean assessed(String id) {
        if(id==null) return false;
        return events.findByEventId(id).filter(e -> "COMPLETED".equals(e.getStatus()) && e.getRiskLevel()!=null && e.getRiskScore()!=null && e.getAgentReason()!=null).isPresent();
    }
}

