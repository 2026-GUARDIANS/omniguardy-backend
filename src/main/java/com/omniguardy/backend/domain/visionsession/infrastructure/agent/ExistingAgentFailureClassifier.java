package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingAgentFailureClassifier;
import com.omniguardy.backend.global.error.exception.BusinessException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RetriableException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class ExistingAgentFailureClassifier implements StreamingAgentFailureClassifier {
    @Override public Kind classify(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof IOException || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof RetriableException || current instanceof TransientDataAccessException) return Kind.RETRYABLE;
            if (current instanceof HttpException http && (http.statusCode() == 408 || http.statusCode() == 429 || http.statusCode() >= 500))
                return Kind.RETRYABLE;
        }
        return failure instanceof BusinessException || failure instanceof HttpException ? Kind.PERMANENT : Kind.UNKNOWN;
    }
    @Override public String code(Throwable failure) {
        return failure instanceof BusinessException business ? business.getErrorCode().getCode()
                : classify(failure) == Kind.RETRYABLE ? "RETRYABLE_AGENT_FAILURE" : "AGENT_EXECUTION_FAILURE";
    }
}
