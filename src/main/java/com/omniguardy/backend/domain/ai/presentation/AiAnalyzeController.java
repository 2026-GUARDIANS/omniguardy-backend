package com.omniguardy.backend.domain.ai.presentation;

import com.omniguardy.backend.domain.ai.application.model.AnalysisResult;
import com.omniguardy.backend.domain.ai.application.usecase.AnalyzeMediaUseCase;
import com.omniguardy.backend.domain.ai.domain.error.AiErrorCode;
import com.omniguardy.backend.domain.ai.presentation.dto.response.AiAnalyzeResponse;
import com.omniguardy.backend.domain.ai.presentation.mapper.AiPresentationMapper;
import com.omniguardy.backend.domain.ai.presentation.success.AiSuccessCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiAnalyzeController {
    private final AnalyzeMediaUseCase analyzeMediaUseCase;
    private final AiPresentationMapper mapper;

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AiAnalyzeResponse>> analyze(@RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        if (userDetails == null) throw new BusinessException(AiErrorCode.AUTHENTICATION_REQUIRED);
        if (file == null || file.isEmpty()) throw new BusinessException(AiErrorCode.MEDIA_FILE_REQUIRED);
        AnalysisResult result = analyzeMediaUseCase.analyze(mapper.toMediaFile(file));
        return SuccessResponse.of(AiSuccessCode.ANALYSIS_COMPLETED, mapper.toResponse(result));
    }
}
