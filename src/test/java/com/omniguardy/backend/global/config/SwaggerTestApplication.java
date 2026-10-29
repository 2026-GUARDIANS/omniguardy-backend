package com.omniguardy.backend.global.config;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@Import({SecurityConfig.class, OpenApiConfig.class, SwaggerTestApplication.TestController.class})
class SwaggerTestApplication {

    @Bean
    JwtProvider jwtProvider() {
        return mock(JwtProvider.class);
    }

    @Bean
    CustomUserDetailsService customUserDetailsService() {
        return mock(CustomUserDetailsService.class);
    }

    @Bean
    ErrorResponseWriter errorResponseWriter() {
        return new ErrorResponseWriter(new ObjectMapper());
    }

    @RestController
    static class TestController {
        @GetMapping("/hello")
        String publicApi() {
            return "hello";
        }

        @GetMapping("/api/protected-test")
        String protectedApi() {
            return "protected";
        }
    }
}
