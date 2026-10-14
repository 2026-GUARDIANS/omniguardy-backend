package com.omniguardy.backend.domain.detection.presentation.mapper;

import com.omniguardy.backend.domain.detection.application.model.AudioReceipt;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.model.VideoReceipt;
import com.omniguardy.backend.domain.detection.presentation.dto.response.AudioUploadResponseDto;
import com.omniguardy.backend.domain.detection.presentation.dto.response.VideoUploadResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class DetectionPresentationMapper {
    public MediaFile toMediaFile(MultipartFile file) throws IOException {
        return new MediaFile(file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType(), file == null ? null : file.getBytes());
    }

    public VideoUploadResponseDto toResponse(VideoReceipt receipt) {
        return new VideoUploadResponseDto(receipt.eventId(), receipt.filename(), receipt.vision());
    }

    public AudioUploadResponseDto toResponse(AudioReceipt receipt) {
        return new AudioUploadResponseDto(receipt.eventId(), receipt.filename(), receipt.size(),
                receipt.eventType(), receipt.probability());
    }
}
