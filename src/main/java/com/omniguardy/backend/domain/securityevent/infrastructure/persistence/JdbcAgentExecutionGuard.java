package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.application.port.out.AgentExecutionGuard;
import com.omniguardy.backend.domain.securityevent.domain.error.AgentExecutionConflict;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class JdbcAgentExecutionGuard implements AgentExecutionGuard {
    private final JdbcTemplate jdbc;

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim begin(String eventId, String reservedToken) {
        var event = jdbc.queryForMap("SELECT status,risk_level,risk_score,agent_reason FROM security_events WHERE event_id=? FOR UPDATE", eventId);
        if ("COMPLETED".equals(event.get("status")) && event.get("risk_level") != null
                && event.get("risk_score") != null && event.get("agent_reason") != null)
            return new Claim(eventId, null, true, new com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment(
                    com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel.valueOf(event.get("risk_level").toString()),
                    ((Number)event.get("risk_score")).intValue(),event.get("agent_reason").toString()));
        var rows = jdbc.queryForList("SELECT * FROM security_agent_executions WHERE event_id=?", eventId);
        String token = reservedToken == null ? UUID.randomUUID().toString() : reservedToken;
        if (rows.isEmpty()) {
            if (reservedToken != null) throw new AgentExecutionConflict();
            jdbc.update("INSERT INTO security_agent_executions (event_id,execution_token,execution_state,attempts,created_at,updated_at) VALUES (?,?,'CALLING',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))", eventId, token);
        } else {
            var row = rows.getFirst();
            boolean reserved = reservedToken != null && reservedToken.equals(row.get("execution_token")) && "RESERVED".equals(row.get("execution_state"));
            boolean retryable = reservedToken == null && "RETRYABLE".equals(row.get("execution_state"));
            if (!reserved && !retryable) throw new AgentExecutionConflict();
            jdbc.update("UPDATE security_agent_executions SET execution_token=?,execution_state='CALLING',attempts=attempts+1,updated_at=CURRENT_TIMESTAMP(6) WHERE event_id=?", token, eventId);
        }
        return new Claim(eventId, token, false, null);
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(Claim claim) { finish(claim, "SUCCEEDED"); }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Claim claim, boolean uncertain) { finish(claim, uncertain ? "UNCERTAIN" : "RETRYABLE"); }
    private void finish(Claim claim, String state) {
        jdbc.update("UPDATE security_agent_executions SET execution_state=?,updated_at=CURRENT_TIMESTAMP(6) WHERE event_id=? AND execution_token=? AND execution_state='CALLING'", state, claim.eventId(), claim.token());
    }
}
