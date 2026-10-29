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
    void documentsConcreteRequestsResponsesAndContractErrors() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.summary").value("회원가입"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.requestBody.content['application/json'].examples['회원가입 요청'].value.email")
                        .value("guard@example.com"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['201'].content['application/json'].examples.SIGNUP_SUCCESS.value.data.name")
                        .value("김가디"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['409'].content['application/json'].examples.EMAIL_ALREADY_EXISTS.value.code")
                        .value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.components.schemas.SignupRequestDto.properties.email.example")
                        .value("guard@example.com"))
                .andExpect(jsonPath("$.paths['/api/internal/ai/audio-result'].post.security[0].deviceApiKey")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/internal/ai/audio-result'].post.requestBody.content['application/json'].examples['노크 감지'].value.predicted_class")
                        .value("knock"))
                .andExpect(jsonPath("$.paths['/api/internal/ai/audio-result'].post.responses['200'].content['application/json'].examples.AUDIO_RESULT_RECEIVED.value.data.outcome")
                        .value("TRIGGERED"))
                .andExpect(jsonPath("$.paths['/api/edge/video'].post.responses['413'].content['application/json'].examples.VIDEO_FILE_TOO_LARGE.value.status")
                        .value(413))
                .andExpect(jsonPath("$.paths['/api/security-events'].get.responses['200'].content['application/json'].examples['첫 페이지'].value.content[0].eventId")
                        .value("evt-video-20261029-001"))
                .andExpect(jsonPath("$.paths['/api/security-events/{eventId}'].get.responses['404'].content['application/json'].examples.SECURITY_EVENT_NOT_FOUND.value.code")
                        .value("SECURITY_EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.paths['/api/tts'].post.requestBody.content['application/json'].examples['TTS 요청'].value.message")
                        .value("문 앞에서 물러나 주세요. 보호자에게 알림이 전송되었습니다."))
                .andExpect(jsonPath("$.paths['/api/tts'].post.responses['503'].content['application/json'].examples.TTS_DISABLED.value.code")
                        .value("TTS_DISABLED"))
                .andExpect(jsonPath("$.paths['/api/device-tokens'].post.responses['201'].content['application/json'].examples.DEVICE_TOKEN_REGISTERED.value.code")
                        .value("DEVICE_TOKEN_REGISTERED"))
                .andExpect(jsonPath("$.paths['/api/sounds/{type}'].post.responses['204']").exists());
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
