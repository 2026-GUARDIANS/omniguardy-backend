package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.detection.application.port.out.AudioAnalysisPort;
import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import com.omniguardy.backend.domain.detection.application.port.out.MediaStoragePort;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.mock.env.MockEnvironment;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceiveAudioUseCaseTest {
    private final AudioAnalysisPort audio = mock(AudioAnalysisPort.class);
    private final MediaStoragePort storage = mock(MediaStoragePort.class);
    private final CameraCommandPort camera = mock(CameraCommandPort.class);
    private final SecurityEventRepository repository = mock(SecurityEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockEnvironment environment = new MockEnvironment().withProperty("omniguardy.demo.user-id", "1");
    private final ReceiveAudioUseCase useCase = new ReceiveAudioUseCase(audio, storage, camera, repository,
            new DemoUserResolver(users, environment));
    private final MediaFile file = new MediaFile("chunk.wav", "audio/wav", new byte[]{1, 2, 3});

    @Test void backgroundDoesNotCreateEventOrStartCamera() {
        environment.setActiveProfiles("dev");
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.BACKGROUND, 0.982));
        var receipt = useCase.receive(file);
        assertNull(receipt.eventId());
        assertEquals(AudioEventType.BACKGROUND, receipt.eventType());
        verifyNoInteractions(storage, camera, repository, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void knockCreatesUuidEventAndStartsCamera(String profile) {
        environment.setActiveProfiles(profile);
        User user = mock(User.class);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.KNOCK, 0.995));
        when(storage.saveAudio(anyString(), any())).thenAnswer(invocation ->
                new MediaStoragePort.StoredMedia(invocation.getArgument(0) + "_chunk.wav", "./uploads/audio/file"));
        var receipt = useCase.receive(file);
        assertDoesNotThrow(() -> java.util.UUID.fromString(receipt.eventId()));
        verify(repository).save(argThat(event -> event.getUser() == user && receipt.eventId().equals(event.getEventId())
                && receipt.eventId().equals(event.getTriggerId())
                && event.getTriggerType() == com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType.AUDIO
                && AudioEventType.KNOCK == event.getAudioEventType()
                && event.getTriggeredAt() != null
                && "CAMERA_REQUESTED".equals(event.getStatus())));
        var commandCaptor = ArgumentCaptor.forClass(CameraStartCommand.class);
        verify(camera).startCamera(commandCaptor.capture());
        CameraStartCommand command = commandCaptor.getValue();
        assertEquals(receipt.eventId(), command.triggerId());
        assertEquals(TriggerType.AUDIO, command.triggerType());
        assertEquals(receipt.eventId(), command.securityEventId());
        assertNotNull(command.triggeredAt());
        assertEquals(10, command.duration());
    }

    @Test void secondHandleInsideCooldownCreatesNothing() {
        environment.setActiveProfiles("dev");
        when(users.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.HANDLE, 0.964));
        when(storage.saveAudio(anyString(), any())).thenReturn(new MediaStoragePort.StoredMedia("a.wav", "p"));
        assertNotNull(useCase.receive(file).eventId());
        assertNull(useCase.receive(file).eventId());
        verify(camera, times(1)).startCamera(any(CameraStartCommand.class));
        verify(repository, times(1)).save(any(SecurityEvent.class));
        verify(users, times(1)).findById(1L);
    }

    @Test void missingDemoUserFailsBeforeStorageOrCamera() {
        environment.setActiveProfiles("dev");
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.KNOCK, 0.99));
        assertEquals(UserErrorCode.USER_NOT_FOUND,
                assertThrows(BusinessException.class, () -> useCase.receive(file)).getErrorCode());
        verifyNoInteractions(storage, camera, repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void missingConfigurationFailsBeforeStorageOrCamera(String profile) {
        environment.setActiveProfiles(profile);
        environment.getPropertySources().remove("mockProperties");
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.KNOCK, 0.99));
        assertEquals(UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED,
                assertThrows(BusinessException.class, () -> useCase.receive(file)).getErrorCode());
        verifyNoInteractions(users, storage, camera, repository);
    }
}

