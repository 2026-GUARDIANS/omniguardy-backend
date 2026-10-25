package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.detection.application.service.AudioDetectionService;
import com.omniguardy.backend.domain.detection.domain.error.DuplicateAudioDetectionException;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReceiveAudioEventUseCaseTest {
    private final MediaStoragePort storage = mock(MediaStoragePort.class);
    private final CameraCommandPort camera = mock(CameraCommandPort.class);
    private final SecurityEventRepository events = mock(SecurityEventRepository.class);
    private final DemoUserResolver users = mock(DemoUserResolver.class);
    private final AudioDetectionReceiptPort receipts = mock(AudioDetectionReceiptPort.class);
    private final AudioDetectionService service = new AudioDetectionService(storage, camera, events, users, receipts);
    private final ReceiveAudioEventUseCase useCase = new ReceiveAudioEventUseCase(service);

    @ParameterizedTest
    @EnumSource(value = AudioEventType.class, names = {"KNOCK", "HANDLE"})
    void detectedAudioCreatesEventWithoutFile(AudioEventType type) {
        when(users.resolve()).thenReturn(Optional.of(mock(User.class)));
        var result = useCase.receive("pi-1", new AudioAnalysis(type, 0.01));
        assertEquals(AudioDetectionResult.Outcome.TRIGGERED, result.outcome());
        verify(events).save(argThat(event -> event.getAudioPath() == null
                && event.getAudioEventType() == type && event.getAudioProbability() == 0.01
                && event.getEventId().equals(event.getTriggerId())
                && event.getEventId().equals(result.eventId())
                && "CAMERA_REQUESTED".equals(event.getStatus())));
        verify(camera).startCamera(argThat(command -> command.duration() == 10
                && command.triggerType() == TriggerType.AUDIO
                && command.triggerId().equals(result.eventId())
                && command.securityEventId().equals(result.eventId())));
        verifyNoInteractions(storage);
    }

    @Test void backgroundIsRecordedWithoutConsumingCooldown() {
        assertEquals(AudioDetectionResult.Outcome.BACKGROUND_IGNORED,
                useCase.receive("background", new AudioAnalysis(AudioEventType.BACKGROUND, 0.9)).outcome());
        verify(receipts).claim("background");
        assertEquals(0L, ReflectionTestUtils.getField(service, "lastCameraTriggeredAt"));
        verifyNoInteractions(camera, storage, events, users);
    }

    @Test void cooldownIsSharedWithLegacyUploadAndExpiresAtTwelveSeconds() {
        when(users.resolve()).thenReturn(Optional.of(mock(User.class)));
        setTime(100_000);
        useCase.receive("first", new AudioAnalysis(AudioEventType.KNOCK, 0.9));
        var legacy = new ReceiveAudioUseCase(file -> new AudioAnalysis(AudioEventType.HANDLE, 0.9), service);
        setTime(111_999);
        assertNull(legacy.receive(new MediaFile("a.wav", "audio/wav", new byte[]{1})).eventId());
        assertEquals(AudioDetectionResult.Outcome.COOLDOWN_SUPPRESSED,
                useCase.receive("suppressed", new AudioAnalysis(AudioEventType.HANDLE, 0.9)).outcome());
        setTime(112_000);
        assertNotNull(useCase.receive("next", new AudioAnalysis(AudioEventType.HANDLE, 0.9)).eventId());
        verify(camera, times(2)).startCamera(any());
        verify(receipts).claim("suppressed");
        verifyNoInteractions(storage);
    }

    @Test void duplicateDoesNotReachCooldownOrSideEffects() {
        doThrow(new DuplicateAudioDetectionException(new RuntimeException())).when(receipts).claim("duplicate");
        assertEquals(AudioDetectionResult.Outcome.DUPLICATE,
                useCase.receive("duplicate", new AudioAnalysis(AudioEventType.KNOCK, 0.9)).outcome());
        assertEquals(0L, ReflectionTestUtils.getField(service, "lastCameraTriggeredAt"));
        verifyNoInteractions(camera, storage, events, users);
    }

    @Test void invalidInputDoesNotClaimReceipt() {
        assertThrows(BusinessException.class, () -> useCase.receive(" ", new AudioAnalysis(AudioEventType.KNOCK, 0.9)));
        assertThrows(BusinessException.class, () -> useCase.receive("id", new AudioAnalysis(AudioEventType.KNOCK, Double.NaN)));
        assertThrows(BusinessException.class, () -> useCase.receive("id", new AudioAnalysis(AudioEventType.KNOCK, 1.1)));
        verifyNoInteractions(receipts);
    }

    private void setTime(long millis) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC));
    }
}
