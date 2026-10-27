package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.port.in.QueryVideoUrlInputPort;
import com.omniguardy.backend.domain.securityevent.application.port.out.VideoUrlPort;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
@RequiredArgsConstructor
public class QueryVideoUrlUseCase implements QueryVideoUrlInputPort {
    private final SecurityEventRepository repository;
    private final VideoUrlPort urls;
    @Override public String query(String eventId, Long userId) {
        var event = repository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException(SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND));
        if (event.getVideoPath() == null) throw new BusinessException(SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND);
        return urls.sign(event.getVideoPath());
    }
}
