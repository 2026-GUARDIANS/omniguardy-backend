package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.usecase.QuerySecurityEventsUseCase;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.Filter;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityEventControllerTest {
    @Configuration @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({SecurityConfig.class, SecurityEventController.class, QuerySecurityEventsUseCase.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean SecurityEventRepository repository() { return mock(SecurityEventRepository.class); }
        @Bean JwtProvider jwt() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter errors() { return new ErrorResponseWriter(new ObjectMapper()); }
    }
    @ParameterizedTest @ValueSource(strings = {"dev", "prod"})
    void requiresJwtAndRestrictsBothRoutesToAuthenticatedOwner(String profile) throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().setActiveProfiles(profile);
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "jwt.secret-key=test-secret-key-at-least-thirty-two-bytes",
                    "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000");
            context.register(Config.class); context.refresh();
            var jwt = context.getBean(JwtProvider.class);
            var users = context.getBean(CustomUserDetailsService.class);
            for (long id : List.of(1L, 2L)) {
                String email = "user" + id + "@example.com";
                when(jwt.validateToken("token-" + id)).thenReturn(true);
                when(jwt.getEmail("token-" + id)).thenReturn(email);
                var user = User.builder().email(email).password("secret").name("private").phoneNumber("010" + id).build();
                ReflectionTestUtils.setField(user, "id", id);
                when(users.loadUserByUsername(email)).thenReturn(new CustomUserDetails(user));
            }
            var repository = context.getBean(SecurityEventRepository.class);
            var event = SecurityEvent.builder().eventId("owned").status("COMPLETED").riskLevel("HIGH").riskScore(90)
                    .visionAnalysisMode("exhibition_rules").build();
            when(repository.findAllByUserId(anyLong(), any())).thenAnswer(invocation ->
                    new PageImpl<>(invocation.getArgument(0).equals(1L) ? List.of(event) : List.of(), invocation.getArgument(1),
                            invocation.getArgument(0).equals(1L) ? 1 : 0));
            when(repository.findByEventIdAndUserId("owned", 1L)).thenReturn(Optional.of(event));
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            for (String path : List.of("/api/security-events", "/api/security-events/owned")) {
                mvc.perform(get(path)).andExpect(status().isUnauthorized());
                mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(repository);
            mvc.perform(get("/api/security-events?userId=2").header("Authorization", "Bearer token-1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("size").value(20))
                    .andExpect(jsonPath("content[0].eventId").value("owned")).andExpect(jsonPath("totalElements").value(1));
            mvc.perform(get("/api/security-events?page=1&size=2").header("Authorization", "Bearer token-2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("page").value(1))
                    .andExpect(jsonPath("content").isEmpty()).andExpect(jsonPath("totalElements").value(0));
            mvc.perform(get("/api/security-events/owned").header("Authorization", "Bearer token-1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("riskScore").value(90))
                    .andExpect(jsonPath("visionAnalysisMode").value("exhibition_rules"));
            for (String eventId : List.of("owned", "missing")) {
                mvc.perform(get("/api/security-events/" + eventId).header("Authorization", "Bearer token-2"))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("SECURITY_EVENT_NOT_FOUND"));
            }
            mvc.perform(get("/api/security-events?page=bad").header("Authorization", "Bearer token-1"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/security-events?size=0").header("Authorization", "Bearer token-1"))
                    .andExpect(status().isBadRequest());
            verify(repository).findAllByUserId(eq(1L), argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20));
            verify(repository).findAllByUserId(eq(2L), argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 2));
            verify(repository, never()).findByEventId(anyString());
        }
    }
}
