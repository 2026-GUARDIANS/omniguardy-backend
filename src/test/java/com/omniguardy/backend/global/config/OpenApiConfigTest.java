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
}
