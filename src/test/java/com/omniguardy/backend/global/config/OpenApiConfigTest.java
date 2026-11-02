package com.omniguardy.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void definesApiMetadataAndBearerJwtScheme() {
        OpenAPI openApi = config.omniguardyOpenAPI();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("OmniGuardy API");
        assertThat(openApi.getInfo().getDescription())
                .isEqualTo("OmniGuardy Backend REST API Documentation");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH).getScheme())
                .isEqualTo("bearer");
        assertThat(openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH).getBearerFormat())
                .isEqualTo("JWT");
        assertThat(openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.DEVICE_API_KEY).getName())
                .isEqualTo("X-Device-Api-Key");
    }

    @Test
    void appliesJwtOnlyToJwtProtectedApis() {
        Operation publicOperation = new Operation();
        Operation protectedOperation = new Operation();
        Operation deviceOperation = new Operation();
        OpenAPI openApi = config.omniguardyOpenAPI().paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(publicOperation))
                .addPathItem("/api/security-events", new PathItem().get(protectedOperation))
                .addPathItem("/api/internal/ai/audio-result", new PathItem().post(deviceOperation)));

        config.jwtSecurityOpenApiCustomizer().customise(openApi);

        assertThat(publicOperation.getSecurity()).isNull();
        assertThat(deviceOperation.getSecurity()).isNull();
        assertThat(protectedOperation.getSecurity()).hasSize(1);
        assertThat(protectedOperation.getSecurity().getFirst())
                .containsKey(OpenApiConfig.BEARER_AUTH);
    }

    @Test
    void streamingSessionsUseDeviceAuthenticationBeforePublicEdgePattern() {
        Operation register = new Operation();
        Operation query = new Operation();
        Operation legacy = new Operation();
        OpenAPI openApi = config.omniguardyOpenAPI().paths(new Paths()
                .addPathItem("/api/edge/vision/sessions", new PathItem().post(register))
                .addPathItem("/api/edge/vision/sessions/{sessionId}", new PathItem().get(query))
                .addPathItem("/api/edge/video", new PathItem().post(legacy)));
        config.jwtSecurityOpenApiCustomizer().customise(openApi);
        assertThat(register.getSecurity().getFirst()).containsKey(OpenApiConfig.DEVICE_API_KEY);
        assertThat(query.getSecurity().getFirst()).containsKey(OpenApiConfig.DEVICE_API_KEY);
        assertThat(legacy.getSecurity()).isNull();
    }

    @Test
    void visionCallbackUsesDedicatedKeyRatherThanUserJwt() {
        Operation callback = new Operation();
        OpenAPI openApi = config.omniguardyOpenAPI().paths(new Paths()
                .addPathItem("/api/internal/ai/vision/early-events", new PathItem().post(callback)));
        config.jwtSecurityOpenApiCustomizer().customise(openApi);
        assertThat(callback.getSecurity()).hasSize(1);
        assertThat(callback.getSecurity().getFirst()).containsOnlyKeys(OpenApiConfig.VISION_CALLBACK_API_KEY);
        assertThat(openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.VISION_CALLBACK_API_KEY).getName())
                .isEqualTo("X-Vision-Callback-Key");
    }
}
