package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.securityevent.application.model.HomeSummaryResponse;
import com.omniguardy.backend.domain.securityevent.application.port.in.QueryHomeSummaryInputPort;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, HomeController.class})
    static class Config {
        @Bean QueryHomeSummaryInputPort summary() { return mock(QueryHomeSummaryInputPort.class); }
        @Bean JwtProvider jwt() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter errors() { return new ErrorResponseWriter(new ObjectMapper()); }
    }

    @Test
    void returnsAuthenticatedUsersSummaryAndRejectsUnauthenticatedRequest() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "jwt.secret-key=test-secret-key-at-least-thirty-two-bytes",
                    "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000");
            context.register(Config.class);
            context.refresh();
            var jwt = context.getBean(JwtProvider.class);
            var users = context.getBean(CustomUserDetailsService.class);
            when(jwt.validateToken("token")).thenReturn(true);
            when(jwt.getEmail("token")).thenReturn("guard@example.com");
            var user = User.builder().email("guard@example.com").password("secret")
                    .name("guard").phoneNumber("010-1234-5678").build();
            ReflectionTestUtils.setField(user, "id", 17L);
            when(users.loadUserByUsername("guard@example.com")).thenReturn(new CustomUserDetails(user));
            var summary = context.getBean(QueryHomeSummaryInputPort.class);
            when(summary.query(17L)).thenReturn(new HomeSummaryResponse("HIGH",
                    OffsetDateTime.parse("2026-10-31T14:20:00+09:00"), 3));
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();

            mvc.perform(get("/api/home/summary"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get("/api/home/summary").header("Authorization", "Bearer token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("success").value(true))
                    .andExpect(jsonPath("code").value("HOME_SUMMARY_RETRIEVED"))
                    .andExpect(jsonPath("data.securityStatus").value("HIGH"))
                    .andExpect(jsonPath("data.lastDetectedAt").value("2026-10-31T14:20:00+09:00"))
                    .andExpect(jsonPath("data.todayEventCount").value(3));
            verify(summary).query(17L);
        }
    }
}
