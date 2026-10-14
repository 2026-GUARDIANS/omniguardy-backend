package com.omniguardy.backend.domain.tts.presentation.mapper;

import com.omniguardy.backend.domain.tts.application.model.PublishTtsCommand;
import com.omniguardy.backend.domain.tts.presentation.dto.request.TtsRequestDto;
import org.springframework.stereotype.Component;

@Component
public class TtsPresentationMapper {
    public PublishTtsCommand toCommand(TtsRequestDto request) {
        return new PublishTtsCommand(request.getMessage());
    }
}
