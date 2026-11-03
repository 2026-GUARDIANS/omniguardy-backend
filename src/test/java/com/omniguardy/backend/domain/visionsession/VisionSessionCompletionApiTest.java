package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.CompleteVisionSessionService;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.presentation.VisionSessionCompletionController;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
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
import java.time.*;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.KEYPAD;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(VisionSessionCompletionApiTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"omniguardy.device.api-key=device-key", "omniguardy.vision.callback.api-key=vision-key",
        "jwt.secret-key=test-secret-at-least-32-bytes-long", "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000"})
class VisionSessionCompletionApiTest {
    static final String URL = "/api/edge/vision/sessions/session/complete";
    @Autowired WebApplicationContext context;
    @Autowired VisionSessionRepository sessions;
    @Autowired VisionPollingRepository jobs;
    @Autowired KeypadTriggerPort keypad;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(sessions, jobs, keypad);
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger")));
        when(keypad.exists("trigger")).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    VisionPollingJob job(VisionProcessingState state) {
        var now = Instant.parse("2026-11-02T00:00:00Z");
        return new VisionPollingJob(new VisionBinding("session", KEYPAD, "trigger", null), state,
                now, now.plusSeconds(300), now, null, null, 0, null, null, null);
    }
    @Test void acceptsBodylessCompletionAndRetriesWithExistingEnvelope() throws Exception {
        when(jobs.enqueue(any(), any())).thenReturn(job(VisionProcessingState.PENDING));
        mvc.perform(post(URL).header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("code").value("VISION_COMPLETION_ACCEPTED"))
                .andExpect(jsonPath("data.processingState").value("PENDING"))
                .andExpect(jsonPath("data.sessionId").value("session")).andExpect(jsonPath("data.fallbackRequired").value(false))
                .andExpect(jsonPath("data.leaseToken").doesNotExist());
        when(jobs.find("session")).thenReturn(Optional.of(job(VisionProcessingState.SUCCEEDED)));
        mvc.perform(post(URL).header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.processingState").value("SUCCEEDED"));
        verify(jobs, times(1)).enqueue(any(), any());
    }
    @Test void unsolicitedFailureBodyDoesNotCreateAFailureNotificationContract() throws Exception {
        when(jobs.enqueue(any(), any())).thenReturn(job(VisionProcessingState.PENDING));
        mvc.perform(post(URL).header("X-Device-Api-Key", "device-key").contentType("application/json")
                        .content("{\"status\":\"failed\",\"reason\":\"FRAME_UPLOAD_FAILED\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.processingState").value("PENDING"))
                .andExpect(jsonPath("data.fallbackRequired").value(false));
        verify(jobs).enqueue(any(), any());
    }

    @Test void rejectsUnknownAndInvalidSession() throws Exception {
        mvc.perform(post("/api/edge/vision/sessions/missing/complete").header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("VISION_SESSION_NOT_FOUND"));
        mvc.perform(post("/api/edge/vision/sessions/bad!id/complete").header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isBadRequest());
        verify(jobs, never()).enqueue(any(), any());
    }
    @Test void reusesDeviceAuthenticationAndDoesNotAcceptVisionCallbackKeyOrBearerAlone() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-key")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("Authorization", "Bearer user-token")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("X-Device-Api-Key", "wrong")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions, jobs, keypad);
    }
    @Test void terminalSessionWithoutExistingJobIsNotReopened() throws Exception {
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger").complete()));
        mvc.perform(post(URL).header("X-Device-Api-Key", "device-key")).andExpect(status().isConflict());
        verify(jobs, never()).enqueue(any(), any());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, VisionSessionCompletionController.class, CompleteVisionSessionService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtProvider jwt() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter writer() { return new ErrorResponseWriter(new ObjectMapper()); }
        @Bean VisionSessionRepository sessions() { return mock(VisionSessionRepository.class); }
        @Bean VisionPollingRepository jobs() { return mock(VisionPollingRepository.class); }
        @Bean KeypadTriggerPort keypad() { return mock(KeypadTriggerPort.class); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
        @Bean VisionPollingPolicy policy() { return new VisionPollingPolicy(Duration.ofSeconds(2), Duration.ofMinutes(5), Duration.ofSeconds(30), 10); }
    }
}
