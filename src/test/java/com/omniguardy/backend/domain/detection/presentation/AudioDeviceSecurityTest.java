package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AudioDeviceSecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"omniguardy.device.api-key=security-test-key",
        "jwt.secret-key=security-test-secret-at-least-32-bytes",
        "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000"})
class AudioDeviceSecurityTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test void deviceEndpointRequiresDeviceCredentialEvenWithBearerToken() throws Exception {
        mvc.perform(post("/api/internal/ai/audio-result").contentType("application/json")
                .content(AudioEventControllerTest.BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ai/audio-result").header("Authorization", "Bearer user-token")
                .contentType("application/json").content(AudioEventControllerTest.BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ai/audio-result").header("X-Device-Api-Key", "wrong")
                .contentType("application/json").content(AudioEventControllerTest.BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ai/audio-result").header("X-Device-Api-Key", "security-test-key")
                .contentType("application/json").content(AudioEventControllerTest.BODY))
                .andExpect(status().isOk());
    }

    @Test void deviceKeyDoesNotAuthorizeOtherProtectedRoutesAndLegacyRemainsPublic() throws Exception {
        mvc.perform(get("/api/security-events").header("X-Device-Api-Key", "security-test-key"))
                .andExpect(status().isUnauthorized());
        // No handler is installed here; 404 confirms the legacy permitAll rule passed security.
        mvc.perform(post("/api/edge/audio")).andExpect(status().isNotFound());
    }

    @Test void swaggerRoutesPassSecurityWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isNotFound());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtProvider jwtProvider() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter errorWriter() { return new ErrorResponseWriter(new ObjectMapper()); }
        @Bean ReceiveAudioEventInputPort port() {
            var port = mock(ReceiveAudioEventInputPort.class);
            when(port.receive(anyString(), any())).thenReturn(new AudioDetectionResult(
                    "event", AudioDetectionResult.Outcome.TRIGGERED, null));
            return port;
        }
        @Bean AudioEventController controller(ReceiveAudioEventInputPort port) { return new AudioEventController(port); }
    }
}
