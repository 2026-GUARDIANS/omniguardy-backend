package com.omniguardy.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;

@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    private static final List<PathPattern> PUBLIC_API_PATHS = Arrays.stream(new String[]{
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/hello",
                    "/audio/tts/**",
                    "/api/edge/**",
                    "/api/keypad/**",
                    "/api/sounds/**"
            })
            .map(PathPatternParser.defaultInstance::parse)
            .toList();

    private static final PathPattern AUDIO_DEVICE_API_PATH =
            PathPatternParser.defaultInstance.parse("/api/internal/ai/audio-result");

    @Bean
    public OpenAPI omniguardyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OmniGuardy API")
                        .description("OmniGuardy Backend REST API Documentation")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer jwtSecurityOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            PathContainer requestPath = PathContainer.parsePath(path);
            if (isPublicApi(requestPath) || AUDIO_DEVICE_API_PATH.matches(requestPath)) {
                return;
            }

            pathItem.readOperations().forEach(operation -> operation.setSecurity(
                    List.of(new SecurityRequirement().addList(BEARER_AUTH))));
        });
    }

    private boolean isPublicApi(PathContainer path) {
        return PUBLIC_API_PATHS.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
