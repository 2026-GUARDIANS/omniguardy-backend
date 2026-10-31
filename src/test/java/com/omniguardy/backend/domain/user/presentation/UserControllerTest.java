package com.omniguardy.backend.domain.user.presentation;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.user.application.model.CurrentUserProfile;
import com.omniguardy.backend.domain.user.application.port.in.QueryCurrentUserInputPort;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import jakarta.servlet.Filter;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, UserController.class})
    static class Config {
        @Bean QueryCurrentUserInputPort currentUser() { return mock(QueryCurrentUserInputPort.class); }
        @Bean JwtProvider jwt() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter errors() { return new ErrorResponseWriter(new ObjectMapper()); }
    }

    @Test
    void returnsAuthenticatedUsersProfileAndRejectsUnauthenticatedRequest() throws Exception {
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
                    .name("김가디").phoneNumber("010-1234-5678").build();
            ReflectionTestUtils.setField(user, "id", 17L);
            when(users.loadUserByUsername("guard@example.com")).thenReturn(new CustomUserDetails(user));
            var query = context.getBean(QueryCurrentUserInputPort.class);
            when(query.query(17L)).thenReturn(new CurrentUserProfile(
                    "김가디", "guard@example.com", "010-1234-5678"));
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();

            mvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("success").value(true))
                    .andExpect(jsonPath("code").value("CURRENT_USER_RETRIEVED"))
                    .andExpect(jsonPath("data.name").value("김가디"))
                    .andExpect(jsonPath("data.email").value("guard@example.com"))
                    .andExpect(jsonPath("data.phoneNumber").value("010-1234-5678"))
                    .andExpect(jsonPath("data.password").doesNotExist())
                    .andExpect(jsonPath("data.refreshToken").doesNotExist());
            verify(query).query(17L);
        }
    }
}
