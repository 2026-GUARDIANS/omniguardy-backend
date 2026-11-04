package com.omniguardy.backend.domain.visionsession.application.port.out;

public interface StreamingVideoEventLinkPort {
    enum Result { LINKED, ALREADY_LINKED, NOT_READY, CONFLICT, INVALID_BINDING }
    Result link(StreamingVideoLinkRepository.Candidate candidate);
}
