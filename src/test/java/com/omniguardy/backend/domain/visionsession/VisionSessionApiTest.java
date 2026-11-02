package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.VisionSessionService;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionSession;
import com.omniguardy.backend.domain.visionsession.presentation.VisionSessionController;
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
import java.util.Optional;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.KEYPAD;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(VisionSessionApiTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"omniguardy.device.api-key=test-key",
        "jwt.secret-key=test-secret-at-least-32-bytes-long",
        "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000"})
class VisionSessionApiTest {
    static final String URL = "/api/edge/vision/sessions";
    static final String BODY = """
            {"sessionId":"vision-1","triggerType":"KEYPAD","triggerId":"trg-1"}
            """;
    @Autowired WebApplicationContext context;
    @Autowired VisionSessionRepository sessions;
    @Autowired KeypadTriggerPort triggers;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(sessions, triggers);
        when(triggers.exists("trg-1")).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test void createRetryAndQueryUseConsistentEnvelope() throws Exception {
        mvc.perform(post(URL).header("X-Device-Api-Key", "test-key").contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Location", URL + "/vision-1"))
                .andExpect(jsonPath("code").value("VISION_SESSION_REGISTERED"))
                .andExpect(jsonPath("data.status").value("REGISTERED"));
        var stored = VisionSession.register("vision-1", KEYPAD, null, "trg-1");
        when(sessions.findBySessionId("vision-1")).thenReturn(Optional.of(stored));
        mvc.perform(post(URL).header("X-Device-Api-Key", "test-key").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("code").value("VISION_SESSION_FOUND"));
        mvc.perform(get(URL + "/vision-1").header("X-Device-Api-Key", "test-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.triggerId").value("trg-1"));
        verify(sessions, times(1)).insert(any());
    }

    @Test void bothRoutesRequireDeviceKeyEvenWithBearerToken() throws Exception {
        mvc.perform(get(URL + "/vision-1")).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        for (String key : new String[]{"", "wrong"}) {
            mvc.perform(post(URL).header("X-Device-Api-Key", key).header("Authorization", "Bearer user-token")
                    .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
            mvc.perform(get(URL + "/vision-1").header("X-Device-Api-Key", key))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/security-events").header("X-Device-Api-Key", "test-key"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions);
    }

    @Test void invalidPayloadsAre400AndDoNotInsert() throws Exception {
        for (String body : new String[]{"{", "{}", BODY.replace("KEYPAD", "OTHER"), BODY.replace("vision-1", " "),
                BODY.replace("vision-1", "s".repeat(129)), BODY.replace("trg-1", " "),
                BODY.replace("\"triggerId\"", "\"eventId\""),
                BODY.replace("\"triggerId\":", "\"eventId\":\"event\",\"triggerId\":"),
                BODY.replace("vision-1", "../session")}) {
            mvc.perform(post(URL).header("X-Device-Api-Key", "test-key").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(sessions, never()).insert(any());
    }

    @Test void missingAndConflictingBindingsHaveExplicitErrors() throws Exception {
        mvc.perform(get(URL + "/missing").header("X-Device-Api-Key", "test-key"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("VISION_SESSION_NOT_FOUND"));
        when(triggers.exists("trg-1")).thenReturn(false);
        mvc.perform(post(URL).header("X-Device-Api-Key", "test-key").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("VISION_TRIGGER_NOT_FOUND"));
        when(sessions.findBySessionId("vision-1")).thenReturn(Optional.of(VisionSession.register("vision-1", KEYPAD, null, "other")));
        mvc.perform(post(URL).header("X-Device-Api-Key", "test-key").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("VISION_SESSION_CONFLICT"));
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, VisionSessionController.class, VisionSessionService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtProvider jwtProvider() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter writer() { return new ErrorResponseWriter(new ObjectMapper()); }
        @Bean VisionSessionRepository sessions() { return mock(VisionSessionRepository.class); }
        @Bean KeypadTriggerPort triggers() { return mock(KeypadTriggerPort.class); }
        @Bean SecurityEventRepository events() { return mock(SecurityEventRepository.class); }
    }
}
