package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.visionsession.application.port.out.VisionFallbackFailureClassifier;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.util.*;
@Component
public class FallbackFailureClassifier implements VisionFallbackFailureClassifier {
    @Override public boolean permanent(Throwable failure) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable e=failure; e!=null && seen.add(e); e=e.getCause()) {
            if(e instanceof VisionFallbackException f) return f.permanent();
            if(e instanceof IllegalArgumentException) return true;
            if(e instanceof S3Exception s) return s.statusCode()>=400 && s.statusCode()<500 && s.statusCode()!=404 && s.statusCode()!=408 && s.statusCode()!=429;
            if(e instanceof RestClientResponseException http) {
                int s=http.getStatusCode().value(); return s>=400 && s<500 && s!=408 && s!=429;
            }
            if(e instanceof BusinessException business && "VISION_RESPONSE_CONTRACT_INVALID".equals(business.getErrorCode().getCode())) return true;
        }
        return false; // Unknown read/Vision failures remain bounded by max attempts.
    }
    @Override public String reason(Throwable failure) {
        if(failure instanceof VisionFallbackException f) return f.getMessage();
        if(failure instanceof IllegalArgumentException) return "INVALID_FALLBACK_INPUT";
        if(failure instanceof BusinessException b) return b.getErrorCode().getCode();
        return permanent(failure) ? "PERMANENT_SOURCE_OR_VISION_FAILURE" : "RETRYABLE_SOURCE_OR_VISION_FAILURE";
    }
}

