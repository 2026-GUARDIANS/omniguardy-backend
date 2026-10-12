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
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceiveAudioUseCaseTest {
    private final AudioAnalysisPort audio = mock(AudioAnalysisPort.class);
    private final MediaStoragePort storage = mock(MediaStoragePort.class);
    private final CameraCommandPort camera = mock(CameraCommandPort.class);
    private final SecurityEventRepository repository = mock(SecurityEventRepository.class);
    private final ReceiveAudioUseCase useCase = new ReceiveAudioUseCase(audio, storage, camera, repository);
    private final MediaFile file = new MediaFile("chunk.wav", "audio/wav", new byte[]{1, 2, 3});

    @Test void backgroundDoesNotCreateEventOrStartCamera() {
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.BACKGROUND, 0.982));
        var receipt = useCase.receive(file);
        assertNull(receipt.eventId());
        assertEquals(AudioEventType.BACKGROUND, receipt.eventType());
        verifyNoInteractions(storage, camera, repository);
    }

    @Test void knockCreatesUuidEventAndStartsCamera() {
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.KNOCK, 0.995));
        when(storage.saveAudio(anyString(), any())).thenAnswer(invocation ->
                new MediaStoragePort.StoredMedia(invocation.getArgument(0) + "_chunk.wav", "./uploads/audio/file"));
        var receipt = useCase.receive(file);
        assertDoesNotThrow(() -> java.util.UUID.fromString(receipt.eventId()));
        verify(repository).save(argThat(event -> receipt.eventId().equals(event.getEventId())
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
        when(audio.analyze(any())).thenReturn(new AudioAnalysis(AudioEventType.HANDLE, 0.964));
        when(storage.saveAudio(anyString(), any())).thenReturn(new MediaStoragePort.StoredMedia("a.wav", "p"));
        assertNotNull(useCase.receive(file).eventId());
        assertNull(useCase.receive(file).eventId());
        verify(camera, times(1)).startCamera(any(CameraStartCommand.class));
        verify(repository, times(1)).save(any(SecurityEvent.class));
    }
}

