package com.omniguardy.backend.domain.tts.presentation;

import com.omniguardy.backend.domain.tts.application.usecase.PublishTtsUseCase;
import com.omniguardy.backend.domain.tts.presentation.dto.request.TtsRequestDto;
import com.omniguardy.backend.domain.tts.presentation.mapper.TtsPresentationMapper;
import com.omniguardy.backend.domain.tts.presentation.success.TtsSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tts")
public class TtsController {
    private final PublishTtsUseCase publishTtsUseCase;
    private final TtsPresentationMapper mapper;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> publishTts(@AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TtsRequestDto request) {
        publishTtsUseCase.publish(mapper.toCommand(request));
        return SuccessResponse.of(TtsSuccessCode.MESSAGE_PUBLISHED, null);
    }
}
