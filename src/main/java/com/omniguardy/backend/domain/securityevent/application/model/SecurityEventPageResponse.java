package com.omniguardy.backend.domain.securityevent.application.model;

import java.util.List;
import org.springframework.data.domain.Page;

public record SecurityEventPageResponse(List<SecurityEventResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {
    public static SecurityEventPageResponse from(Page<SecurityEventResponse> result) {
        return new SecurityEventPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
