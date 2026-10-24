package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult.Outcome;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.domain.detection.presentation.dto.request.AudioEventRequestDto;
import com.omniguardy.backend.domain.detection.presentation.success.DetectionSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai")
public class AudioEventController {
    private final ReceiveAudioEventInputPort receiveAudioEvent;

    @PostMapping(value = "/audio-result", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AudioEventResponse>> receive(@Valid @RequestBody AudioEventRequestDto request) {
        var result = receiveAudioEvent.receive(request.detectionId(), request.toAnalysis());
        return SuccessResponse.of(DetectionSuccessCode.AUDIO_RESULT_RECEIVED,
                new AudioEventResponse(request.detectionId(), result.eventId(), result.outcome()));
    }

    public record AudioEventResponse(String detectionId, String eventId, Outcome outcome) {}
}
