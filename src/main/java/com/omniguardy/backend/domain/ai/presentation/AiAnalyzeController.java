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
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@Tag(name = "AI Analysis", description = "인증 사용자의 미디어 AI 분석 API")
public class AiAnalyzeController {
    private final AnalyzeMediaUseCase analyzeMediaUseCase;
    private final AiPresentationMapper mapper;

    @Operation(summary = "미디어 AI 분석", description = "영상 또는 음성 파일을 업로드해 Vision/Audio 분석 결과를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.AiAnalyzeEnvelope.class),
                            examples = @ExampleObject(name = "AI_ANALYSIS_COMPLETED", value = AI_ANALYZE_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 누락 또는 빈 파일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "MEDIA_FILE_REQUIRED", value = EMPTY_MEDIA_FILE),
                            @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Vision 또는 Audio 분석 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "VISION_API_FAILED", value = VISION_API_FAILED),
                            @ExampleObject(name = "AUDIO_API_FAILED", value = AUDIO_API_FAILED)
                    }))
    })
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AiAnalyzeResponse>> analyze(
            @Parameter(description = "분석할 영상 또는 음성 파일", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        if (userDetails == null) throw new BusinessException(AiErrorCode.AUTHENTICATION_REQUIRED);
        if (file == null || file.isEmpty()) throw new BusinessException(AiErrorCode.MEDIA_FILE_REQUIRED);
        AnalysisResult result = analyzeMediaUseCase.analyze(mapper.toMediaFile(file));
        return SuccessResponse.of(AiSuccessCode.ANALYSIS_COMPLETED, mapper.toResponse(result));
    }
}
