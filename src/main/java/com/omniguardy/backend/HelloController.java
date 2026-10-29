package com.omniguardy.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "서버 기본 상태 확인 API")
public class HelloController {

    @Operation(summary = "서버 상태 확인", description = "백엔드 프로세스가 요청을 처리할 수 있는지 확인합니다.")
    @ApiResponse(responseCode = "200", description = "서버 응답 정상",
            content = @Content(mediaType = "text/plain",
                    examples = @ExampleObject(name = "정상 응답", value = "Hello, World!")))
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}
