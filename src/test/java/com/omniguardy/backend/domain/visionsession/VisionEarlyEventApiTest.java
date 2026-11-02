package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.ReceiveVisionEarlyEventService;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionEarlyEventException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.presentation.VisionEarlyEventController;
import com.omniguardy.backend.domain.visionsession.presentation.dto.VisionEarlyEventRequest;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(VisionEarlyEventApiTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"omniguardy.vision.callback.api-key=vision-test-key", "omniguardy.device.api-key=device-test-key",
        "jwt.secret-key=test-secret-at-least-32-bytes-long", "jwt.access-token-expire-time=1800000",
        "jwt.refresh-token-expire-time=1209600000"})
class VisionEarlyEventApiTest {
    static final String URL = "/api/internal/ai/vision/early-events";
    static final String BODY = """
            {"messageType":"EARLY_EVENT","sessionId":"session","triggerId":"trigger","securityEventId":null,
             "triggerType":"KEYPAD","eventInstanceId":"session-signal-001","sequence":1,"provisional":true,
             "event":{"eventType":"SILENT_SIGNAL","detectedFrame":114,"confirmedFrame":114,"capturedAt":"3.800000"},
             "emittedAt":"2026-11-02T14:44:19.167+09:00"}
            """;
    @Autowired WebApplicationContext context;
    @Autowired VisionSessionRepository sessions;
    @Autowired KeypadTriggerPort keypad;
    @Autowired VisionAudioTriggerPort audio;
    @Autowired VisionEarlyEventRepository events;
    MockMvc mvc;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        reset(sessions, keypad, audio, events);
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger")));
        when(keypad.exists("trigger")).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test void acceptsContractExampleAndIdenticalRetryWithEmpty204() throws Exception {
        send(BODY, 204);
        var event = VisionEarlyEventRequest.from(mapper.readTree(BODY)).toDomain();
        doThrow(new DuplicateVisionEarlyEventException(new RuntimeException())).when(events).insert(any());
        when(events.find("session", "session-signal-001")).thenReturn(Optional.of(event));
        send(BODY, 204);
        send(BODY.replace("\"sequence\":1", "\"sequence\":2"), 409);
    }

    @Test void acceptsNullableOrOmittedCapturedAtAndSecurityEventIdAndUnformattedCaptureString() throws Exception {
        send(BODY.replace("\"3.800000\"", "null"), 204);
        send(BODY.replace(",\"capturedAt\":\"3.800000\"", "").replace("\"securityEventId\":null,", ""), 204);
        send(BODY.replace("3.800000", "opaque camera time"), 204);
    }

    @Test void audioUsesSecurityEventIdRatherThanRegistrationEventIdAlias() throws Exception {
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", AUDIO, "event", null)));
        when(audio.findIssuedTriggerId("event")).thenReturn(Optional.of("trigger"));
        var body = BODY.replace("KEYPAD", "AUDIO").replace("\"securityEventId\":null", "\"securityEventId\":\"event\"");
        send(body, 204);
        send(body.replace("securityEventId", "eventId"), 409);
        verify(events, times(1)).insert(any());
    }

    @Test void rejectsMalformedAndCoercedJsonTypesBeforeRepositoryAccess() throws Exception {
        var invalid = new ArrayList<>(List.of("{", "{}", "null", "[]",
                BODY.replace("EARLY_EVENT", "FINAL_RESULT"), BODY.replace("SILENT_SIGNAL", "OTHER"),
                BODY.replace("KEYPAD", "OTHER"), BODY.replace("\"provisional\":true", "\"provisional\":false"),
                BODY.replace("session\"", "../session\""), BODY.replace("session-signal-001", "x".repeat(257)),
                BODY.replace("2026-11-02T14:44:19.167+09:00", "2026-11-02T14:44:19.167")));
        var root = mapper.readTree(BODY);
        for (String name : List.of("messageType", "sessionId", "triggerId", "triggerType", "eventInstanceId", "sequence", "provisional", "event", "emittedAt")) {
            var missing = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
            missing.remove(name); invalid.add(mapper.writeValueAsString(missing));
            var nulled = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
            nulled.putNull(name); invalid.add(mapper.writeValueAsString(nulled));
        }
        for (String field : List.of("sequence", "detectedFrame", "confirmedFrame")) {
            String original = root.has(field) ? "1" : "114";
            for (String value : List.of("\"1\"", "1.5", "true", "null", "9223372036854775808"))
                invalid.add(BODY.replace("\"" + field + "\":" + original, "\"" + field + "\":" + value));
        }
        for (String name : List.of("eventType", "detectedFrame", "confirmedFrame")) {
            var missing = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
            ((tools.jackson.databind.node.ObjectNode) missing.get("event")).remove(name);
            invalid.add(mapper.writeValueAsString(missing));
        }
        invalid.add(BODY.replace("\"provisional\":true", "\"provisional\":\"true\""));
        invalid.add(BODY.replace("\"capturedAt\":\"3.800000\"", "\"capturedAt\":3.8"));
        invalid.add(BODY.replace("\"triggerId\":\"trigger\"", "\"triggerId\":12"));
        for (String body : invalid) send(body, 400);
        verifyNoInteractions(sessions, events, keypad, audio);
    }

    @Test void unknownSessionAndBindingMismatchReturnExplicitErrors() throws Exception {
        when(sessions.findBySessionId("session")).thenReturn(Optional.empty());
        mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-test-key").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("VISION_SESSION_NOT_FOUND"));
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "other")));
        mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-test-key").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("VISION_CALLBACK_BINDING_MISMATCH"));
        verifyNoInteractions(events);
    }

    @Test void requiresDedicatedKeyEvenWithUserJwtOrDeviceKey() throws Exception {
        for (String key : List.of("", "wrong", "device-test-key"))
            mvc.perform(post(URL).header("X-Vision-Callback-Key", key).header("X-Device-Api-Key", "device-test-key")
                    .header("Authorization", "Bearer user-token").contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-test-key", "vision-test-key")
                .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/edge/vision/sessions/session").header("X-Vision-Callback-Key", "vision-test-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/security-events").header("X-Vision-Callback-Key", "vision-test-key"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions, events, keypad, audio);
    }

    @Test void blankServerKeyFailsClosed() throws Exception {
        for (String key : new String[]{"", " ", null}) {
            var request = new MockHttpServletRequest("POST", URL);
            request.addHeader("X-Vision-Callback-Key", "vision-test-key");
            var response = new MockHttpServletResponse();
            new VisionCallbackAuthenticationFilter(key, new ErrorResponseWriter(mapper))
                    .doFilter(request, response, (req, res) -> fail("must reject unconfigured key"));
            assertEquals(401, response.getStatus());
        }
    }

    @Test void databaseFailureIsNotAcknowledgedAsSuccess() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(events).insert(any());
        mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-test-key")
                .contentType("application/json").content(BODY)).andExpect(status().isInternalServerError());
    }

    void send(String body, int expected) throws Exception {
        var result = mvc.perform(post(URL).header("X-Vision-Callback-Key", "vision-test-key")
                .contentType("application/json").content(body)).andExpect(status().is(expected));
        if (expected == 204) result.andExpect(content().string(""));
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, VisionEarlyEventController.class, ReceiveVisionEarlyEventService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtProvider jwtProvider() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter writer() { return new ErrorResponseWriter(new ObjectMapper()); }
        @Bean VisionSessionRepository sessions() { return mock(VisionSessionRepository.class); }
        @Bean KeypadTriggerPort keypad() { return mock(KeypadTriggerPort.class); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
        @Bean VisionEarlyEventRepository events() { return mock(VisionEarlyEventRepository.class); }
    }
}
