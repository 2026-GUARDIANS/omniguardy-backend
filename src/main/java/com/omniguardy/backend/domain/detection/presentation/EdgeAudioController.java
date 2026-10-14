package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.AudioReceipt;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioInputPort;
import com.omniguardy.backend.domain.detection.presentation.dto.response.AudioUploadResponseDto;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.domain.detection.presentation.success.DetectionSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/edge")
public class EdgeAudioController {
    private final ReceiveAudioInputPort receiveAudioUseCase;
    private final DetectionPresentationMapper mapper;

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AudioUploadResponseDto>> uploadAudio(
            @RequestPart("file") MultipartFile file) throws IOException {
        AudioReceipt result = receiveAudioUseCase.receive(mapper.toMediaFile(file));
        return SuccessResponse.of(DetectionSuccessCode.AUDIO_RECEIVED, mapper.toResponse(result));
    }
}
