package com.omniguardy.backend.domain.securityevent.domain.error;

/** No external call was made by this caller. The earlier execution must be reconciled. */
public class AgentExecutionConflict extends com.omniguardy.backend.global.error.exception.BusinessException {
    public AgentExecutionConflict() { super(SecurityEventErrorCode.AGENT_EXECUTION_CONFLICT); }
}
