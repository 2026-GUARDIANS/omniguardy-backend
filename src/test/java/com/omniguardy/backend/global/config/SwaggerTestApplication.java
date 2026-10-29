package com.omniguardy.backend.global.config;

import com.omniguardy.backend.HelloController;
import com.omniguardy.backend.domain.ai.application.usecase.AnalyzeMediaUseCase;
import com.omniguardy.backend.domain.ai.presentation.AiAnalyzeController;
import com.omniguardy.backend.domain.ai.presentation.mapper.AiPresentationMapper;
import com.omniguardy.backend.domain.auth.application.usecase.AuthUseCase;
import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.auth.presentation.AuthController;
import com.omniguardy.backend.domain.auth.presentation.mapper.AuthPresentationMapper;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioInputPort;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveVideoInputPort;
import com.omniguardy.backend.domain.detection.application.port.in.TriggerKeypadCameraUseCase;
import com.omniguardy.backend.domain.detection.presentation.AudioEventController;
import com.omniguardy.backend.domain.detection.presentation.EdgeAudioController;
import com.omniguardy.backend.domain.detection.presentation.EdgeKeypadController;
import com.omniguardy.backend.domain.detection.presentation.EdgeVideoController;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.domain.notification.application.usecase.DeviceTokenService;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.notification.presentation.DeviceTokenController;
import com.omniguardy.backend.domain.notification.presentation.NotificationController;
import com.omniguardy.backend.domain.notification.presentation.mapper.NotificationPresentationMapper;
import com.omniguardy.backend.domain.securityevent.application.port.in.QuerySecurityEventsInputPort;
import com.omniguardy.backend.domain.securityevent.application.port.in.QueryVideoUrlInputPort;
import com.omniguardy.backend.domain.securityevent.presentation.SecurityEventController;
import com.omniguardy.backend.domain.securityevent.presentation.VideoUrlController;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.presentation.SoundController;
import com.omniguardy.backend.domain.tts.application.usecase.PublishTtsUseCase;
import com.omniguardy.backend.domain.tts.presentation.TtsController;
import com.omniguardy.backend.domain.tts.presentation.mapper.TtsPresentationMapper;
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
@Import({
        SecurityConfig.class,
        OpenApiConfig.class,
        HelloController.class,
        AuthController.class,
        AiAnalyzeController.class,
        SoundController.class,
        SecurityEventController.class,
        VideoUrlController.class,
        NotificationController.class,
        DeviceTokenController.class,
        AudioEventController.class,
        EdgeAudioController.class,
        EdgeVideoController.class,
        EdgeKeypadController.class,
        TtsController.class,
        SwaggerTestApplication.TestController.class
})
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

    @Bean AuthUseCase authUseCase() { return mock(AuthUseCase.class); }
    @Bean AuthPresentationMapper authPresentationMapper() { return mock(AuthPresentationMapper.class); }
    @Bean AnalyzeMediaUseCase analyzeMediaUseCase() { return mock(AnalyzeMediaUseCase.class); }
    @Bean AiPresentationMapper aiPresentationMapper() { return mock(AiPresentationMapper.class); }
    @Bean PlaySoundUseCase playSoundUseCase() { return mock(PlaySoundUseCase.class); }
    @Bean QuerySecurityEventsInputPort querySecurityEventsInputPort() { return mock(QuerySecurityEventsInputPort.class); }
    @Bean QueryVideoUrlInputPort queryVideoUrlInputPort() { return mock(QueryVideoUrlInputPort.class); }
    @Bean NotificationService notificationService() { return mock(NotificationService.class); }
    @Bean DeviceTokenService deviceTokenService() { return mock(DeviceTokenService.class); }
    @Bean NotificationPresentationMapper notificationPresentationMapper() { return mock(NotificationPresentationMapper.class); }
    @Bean ReceiveAudioEventInputPort receiveAudioEventInputPort() { return mock(ReceiveAudioEventInputPort.class); }
    @Bean ReceiveAudioInputPort receiveAudioInputPort() { return mock(ReceiveAudioInputPort.class); }
    @Bean ReceiveVideoInputPort receiveVideoInputPort() { return mock(ReceiveVideoInputPort.class); }
    @Bean TriggerKeypadCameraUseCase triggerKeypadCameraUseCase() { return mock(TriggerKeypadCameraUseCase.class); }
    @Bean DetectionPresentationMapper detectionPresentationMapper() { return mock(DetectionPresentationMapper.class); }
    @Bean PublishTtsUseCase publishTtsUseCase() { return mock(PublishTtsUseCase.class); }
    @Bean TtsPresentationMapper ttsPresentationMapper() { return mock(TtsPresentationMapper.class); }

    @RestController
    static class TestController {
        @GetMapping("/api/protected-test")
        String protectedApi() {
            return "protected";
        }
    }
}
