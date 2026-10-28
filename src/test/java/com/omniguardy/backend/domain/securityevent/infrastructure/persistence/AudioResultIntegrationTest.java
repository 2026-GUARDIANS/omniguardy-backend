package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.detection.application.service.AudioDetectionService;
import com.omniguardy.backend.domain.detection.application.usecase.ReceiveAudioEventUseCase;
import com.omniguardy.backend.domain.detection.infrastructure.persistence.AudioDetectionReceiptAdapter;
import com.omniguardy.backend.domain.detection.presentation.AudioEventController;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.infrastructure.persistence.*;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AudioResultIntegrationTest.Config.class)
class AudioResultIntegrationTest {
    @Autowired ReceiveAudioEventUseCase useCase;
    @Autowired AudioDetectionService service;
    @Autowired CameraCommandPort camera;
    @Autowired MediaStoragePort storage;
    @Autowired DataSource dataSource;

    @BeforeEach void resetDatabase() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from audio_detection_receipts");
        jdbc.update("delete from security_events");
        reset(camera, storage);
        ReflectionTestUtils.setField(service, "lastCameraTriggeredAt", 0L);
    }

    @Test void concurrentDuplicateCreatesOneEventEvenAfterCooldownExpires() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<AudioDetectionResult> request = () -> {
                start.await();
                return useCase.receive("same-detection", new AudioAnalysis(AudioEventType.KNOCK, 0.98));
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();
            var outcomes = java.util.Set.of(first.get(15, TimeUnit.SECONDS).outcome(),
                    second.get(15, TimeUnit.SECONDS).outcome());
            assertEquals(java.util.Set.of(AudioDetectionResult.Outcome.TRIGGERED,
                    AudioDetectionResult.Outcome.DUPLICATE), outcomes);
        }
        ReflectionTestUtils.setField(service, "lastCameraTriggeredAt", 0L);
        assertEquals(AudioDetectionResult.Outcome.DUPLICATE,
                useCase.receive("same-detection", new AudioAnalysis(AudioEventType.KNOCK, 0.98)).outcome());
        assertEquals(1, count("security_events"));
        assertEquals(1, count("audio_detection_receipts"));
        assertEquals("CAMERA_REQUESTED", new JdbcTemplate(dataSource)
                .queryForObject("select status from security_events", String.class));
        assertNull(new JdbcTemplate(dataSource).queryForObject("select audio_path from security_events", String.class));
        verify(camera, times(1)).startCamera(any());
        verifyNoInteractions(storage);
    }

    @Test void suppressedAndBackgroundReceiptsSurviveAndCannotTriggerLater() {
        useCase.receive("first", new AudioAnalysis(AudioEventType.HANDLE, 0.9));
        assertEquals(AudioDetectionResult.Outcome.COOLDOWN_SUPPRESSED,
                useCase.receive("suppressed", new AudioAnalysis(AudioEventType.KNOCK, 0.9)).outcome());
        assertEquals(AudioDetectionResult.Outcome.BACKGROUND_IGNORED,
                useCase.receive("background", new AudioAnalysis(AudioEventType.BACKGROUND, 0.9)).outcome());
        ReflectionTestUtils.setField(service, "lastCameraTriggeredAt", 0L);
        assertEquals(AudioDetectionResult.Outcome.DUPLICATE,
                useCase.receive("suppressed", new AudioAnalysis(AudioEventType.KNOCK, 0.9)).outcome());
        assertEquals(1, count("security_events"));
        assertEquals(3, count("audio_detection_receipts"));
    }

    @Test void failedProcessingRollsBackReceiptAndAllowsRetry() {
        doThrow(new IllegalStateException("publish failed")).when(camera).startCamera(any());
        assertThrows(IllegalStateException.class,
                () -> useCase.receive("retry", new AudioAnalysis(AudioEventType.KNOCK, 0.9)));
        assertEquals(0, count("security_events"));
        assertEquals(0, count("audio_detection_receipts"));
        reset(camera);
        // Legacy policy retains the cooldown even after rollback.
        ReflectionTestUtils.setField(service, "lastCameraTriggeredAt", 0L);
        assertEquals(AudioDetectionResult.Outcome.TRIGGERED,
                useCase.receive("retry", new AudioAnalysis(AudioEventType.KNOCK, 0.9)).outcome());
    }

    @Test void jsonRequestReachesRealTransactionalPersistence() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AudioEventController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = """
                {"detectionId":"http-detection","status":"success","predicted_class":"handle",
                 "probabilities":{"background":0.01,"knock":0.01,"handle":0.98},
                 "window_start_seconds":2.0,"cooldown_suppressed":true}
                """;
        mvc.perform(post("/api/internal/ai/audio-result").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("data.outcome").value("TRIGGERED"));
        mvc.perform(post("/api/internal/ai/audio-result").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("data.outcome").value("DUPLICATE"));
        assertEquals(1, count("security_events"));
    }

    private int count(String table) {
        return new JdbcTemplate(dataSource).queryForObject("select count(*) from " + table, Integer.class);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = SpringDataSecurityEventRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:audio-result;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan("com.omniguardy.backend.domain.securityevent.domain.model",
                    "com.omniguardy.backend.domain.user.domain.model", "com.omniguardy.backend.domain.detection.domain.model");
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean AudioDetectionReceiptPort receiptPort() { return new AudioDetectionReceiptAdapter(); }
        @Bean SecurityEventRepository events(SpringDataSecurityEventRepository repository) {
            return new SecurityEventRepositoryAdapter(repository);
        }
        @Bean MediaStoragePort storage() { return mock(MediaStoragePort.class); }
        @Bean CameraCommandPort camera() { return mock(CameraCommandPort.class); }
        @Bean DemoUserResolver users() {
            var resolver = mock(DemoUserResolver.class);
            when(resolver.resolve()).thenReturn(Optional.empty());
            return resolver;
        }
        @Bean AudioDetectionService service(MediaStoragePort storage, CameraCommandPort camera,
                SecurityEventRepository events, DemoUserResolver users, AudioDetectionReceiptPort receipts) {
            return new AudioDetectionService(storage, camera, events, users, receipts);
        }
        @Bean ReceiveAudioEventUseCase useCase(AudioDetectionService service) { return new ReceiveAudioEventUseCase(service); }
    }
}
