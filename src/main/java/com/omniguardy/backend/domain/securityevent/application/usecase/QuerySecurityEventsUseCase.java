package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.model.*;
import com.omniguardy.backend.domain.securityevent.application.port.in.QuerySecurityEventsInputPort;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuerySecurityEventsUseCase implements QuerySecurityEventsInputPort {
    private final SecurityEventRepository repository;

    @Override
    public SecurityEventPageResponse list(Long userId, int page, int size) {
        requireUser(userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return SecurityEventPageResponse.from(repository.findAllByUserId(userId, pageable).map(SecurityEventResponse::from));
    }

    @Override
    public SecurityEventResponse detail(Long userId, String eventId) {
        requireUser(userId);
        return SecurityEventResponse.from(repository.findByEventIdAndUserId(eventId, userId).orElseThrow(
                () -> new BusinessException(SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND)));
    }
    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(GlobalErrorCode.AUTHENTICATION_REQUIRED);
    }
}

