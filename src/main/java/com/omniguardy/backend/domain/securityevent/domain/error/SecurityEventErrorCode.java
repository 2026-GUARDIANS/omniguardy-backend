package com.omniguardy.backend.domain.securityevent.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SecurityEventErrorCode implements BaseErrorCode {
    SECURITY_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SECURITY_EVENT_NOT_FOUND", "보안 이벤트를 찾을 수 없습니다."),
    VISION_ANALYSIS_REQUIRED(HttpStatus.CONFLICT, "VISION_ANALYSIS_REQUIRED", "영상 분석이 완료된 이벤트만 위험도를 판단할 수 있습니다."),
    INVALID_EVENT_STATUS(HttpStatus.CONFLICT, "INVALID_EVENT_STATUS", "현재 보안 이벤트 상태에서는 요청을 처리할 수 없습니다."),
    INVALID_CLASS_PROBABILITIES(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CLASS_PROBABILITIES", "분석 확률 데이터를 변환할 수 없습니다."),
    INVALID_AGENT_CONTEXT(HttpStatus.BAD_REQUEST, "INVALID_AGENT_CONTEXT", "Agent 분석 문맥이 올바르지 않습니다."),
    INVALID_AGENT_ASSESSMENT(HttpStatus.BAD_GATEWAY, "INVALID_AGENT_ASSESSMENT", "Agent 분석 결과가 올바르지 않습니다."),
    AGENT_AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_AI_DISABLED", "Agent AI 기능이 비활성화되어 있습니다."),
    AGENT_AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AGENT_AI_RESPONSE_INVALID", "Agent AI 응답을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
