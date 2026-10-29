package com.omniguardy.backend.global.config;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SwaggerTestApplication.class, properties = {
        "jwt.secret-key=swagger-test-secret-at-least-32-bytes",
        "jwt.access-token-expire-time=1800000",
        "jwt.refresh-token-expire-time=1209600000"
})
@ActiveProfiles("dev")
class SwaggerEndpointIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void exposesSwaggerUiAndOpenApiWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("OmniGuardy API"))
                .andExpect(jsonPath("$.info.description")
                        .value("OmniGuardy Backend REST API Documentation"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/hello'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/protected-test'].get.security[0].bearerAuth").isArray());
    }

    @Test
    void preservesPublicAndJwtProtectedApiBehavior() throws Exception {
        mvc.perform(get("/hello"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/protected-test"))
                .andExpect(status().isUnauthorized());

        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getEmail("valid-token")).thenReturn("swagger@example.com");
        when(userDetailsService.loadUserByUsername("swagger@example.com"))
                .thenReturn(User.withUsername("swagger@example.com")
                        .password("password")
                        .roles("USER")
                        .build());

        mvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }
}
