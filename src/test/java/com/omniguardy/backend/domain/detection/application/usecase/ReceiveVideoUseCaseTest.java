package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.securityevent.application.usecase.AnalyzeAgentRiskUseCase;
import com.omniguardy.backend.domain.securityevent.application.usecase.RecordKeypadVisionUseCase;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceiveVideoUseCaseTest {
    private final VisionAnalysisPort visionPort = mock(VisionAnalysisPort.class);
    private final MediaStoragePort storage = mock(MediaStoragePort.class);
    private final SecurityEventRepository repository = mock(SecurityEventRepository.class);
    private final AnalyzeAgentRiskUseCase agent = mock(AnalyzeAgentRiskUseCase.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReceiveVideoUseCase useCase = new ReceiveVideoUseCase(visionPort, storage, repository,
            agent, mapper, new RecordKeypadVisionUseCase(repository, mapper));
    private final MediaFile file = new MediaFile("video.mp4", "video/mp4", new byte[]{1});
    private final OffsetDateTime time = OffsetDateTime.parse("2026-09-23T12:00:00+09:00");

    @Test void updatesExistingAudioEventAndReusesOriginalTrigger() {
        SecurityEvent event = SecurityEvent.builder().eventId("same-event").triggerId("same-event")
                .triggerType(EventTriggerType.AUDIO).triggeredAt(time).status("CAMERA_REQUESTED").build();
        when(repository.findByEventId("same-event")).thenReturn(Optional.of(event));
        stubStorageAndVision(List.of());
        var receipt = useCase.receive(file, new VideoTriggerContext("same-event", null, TriggerType.AUDIO, null, null));
        assertEquals("same-event", receipt.eventId());
        assertEquals("A18", event.getVisionPrediction());
        assertEquals("[]", event.getVisionEvents());
        assertEquals(time.plusSeconds(10), event.getVisionAnalyzedAt());
        assertEquals("VISION_ANALYZED", event.getStatus());
        verify(visionPort).analyze(eq(file), eq(new VideoTriggerContext(
                "same-event", "same-event", TriggerType.AUDIO, "same-event", time.toString())));
        verify(agent).analyze("same-event");
        verify(repository, times(2)).save(same(event));
    }

    @Test void rejectsUnknownAudioEventWithoutCreatingOne() {
        assertThrows(BusinessException.class, () -> useCase.receive(file,
                new VideoTriggerContext("missing", null, TriggerType.AUDIO, null, null)));
        verify(repository, never()).save(any());
        verifyNoInteractions(storage, visionPort, agent);
    }

    @Test void emptyKeypadVisionDoesNotCreateEventOrInvokeAgent() {
        stubStorageAndVision(List.of());
        assertNull(useCase.receive(file, keypad()).eventId());
        verifyNoInteractions(repository, agent);
        verify(visionPort).analyze(eq(file), eq(keypad()));
    }

    @Test void nullKeypadVisionEventsAreTreatedAsEmpty() {
        stubStorageAndVision(null);
        assertNull(useCase.receive(file, keypad()).eventId());
        verifyNoInteractions(repository, agent);
    }

    @Test void keypadSilentSignalIsSavedBeforeAgentAssessment() {
        stubStorageAndVision(List.of(new VisionEvent("SILENT_SIGNAL", 0.97, 42, Map.of("hand", "left"))));
        var receipt = useCase.receive(file, keypad());
        SecurityEvent event = savedEvent();
        assertEquals(receipt.eventId(), event.getEventId());
        assertNotEquals("trg-keypad", event.getEventId());
        assertEquals("trg-keypad", event.getTriggerId());
        assertEquals(EventTriggerType.KEYPAD, event.getTriggerType());
        assertEquals(time, event.getTriggeredAt());
        assertNull(event.getAudioEventType());
        assertNull(event.getAudioProbability());
        assertNull(event.getAudioPath());
        assertEquals("video-path", event.getVideoPath());
        assertEquals("SILENT_SIGNAL", mapper.readTree(event.getVisionEvents()).get(0).get("eventType").asText());
        assertEquals(time.plusSeconds(10), event.getVisionAnalyzedAt());
        assertEquals("VISION_ANALYZED", event.getStatus());
        verify(agent).analyze(receipt.eventId());
    }

    @Test void unknownKeypadVisionEventIsLoggedWithoutAgentAssessment() {
        stubStorageAndVision(List.of(new VisionEvent("OTHER_EVENT", 0.9, 20, Map.of())));
        var receipt = useCase.receive(file, keypad());
        assertNotNull(receipt.eventId());
        verify(repository).save(any(SecurityEvent.class));
        verifyNoInteractions(agent);
    }

    @Test void rearCloseApproachIsSavedBeforeAgentAssessment() {
        stubStorageAndVision(List.of(new VisionEvent(
                "REAR_CLOSE_APPROACH_SUSPECTED", 0.9, 20, Map.of())));
        var receipt = useCase.receive(file, keypad());
        var order = inOrder(repository, agent);
        order.verify(repository).save(any(SecurityEvent.class));
        order.verify(agent).analyze(receipt.eventId());
    }

    @Test void visionFailureMarksExistingAudioEventFailed() {
        SecurityEvent event = SecurityEvent.builder().eventId("audio").triggeredAt(time)
                .status("CAMERA_REQUESTED").build();
        when(repository.findByEventId("audio")).thenReturn(Optional.of(event));
        when(storage.saveVideo(anyString(), any())).thenReturn(new MediaStoragePort.StoredMedia("v.mp4", "video-path"));
        when(visionPort.analyze(any(), any())).thenThrow(new IllegalStateException("Vision unavailable"));
        assertThrows(IllegalStateException.class, () -> useCase.receive(file,
                new VideoTriggerContext("audio", null, TriggerType.AUDIO, null, null)));
        assertEquals("FAILED", event.getStatus());
        assertEquals("video-path", event.getVideoPath());
        verifyNoInteractions(agent);
    }

    private VideoTriggerContext keypad() {
        return new VideoTriggerContext(null, "trg-keypad", TriggerType.KEYPAD, null, time.toString());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"N1", "A17", "A19"})
    void nonRiskAudioBehaviorsAreLoggedAndCompletedWithoutAgent(String prediction) {
        stubStorageAndVision(prediction, List.of());
        SecurityEvent event = SecurityEvent.builder().eventId("audio-event").status("CAMERA_REQUESTED").build();
        when(repository.findByEventId("audio-event")).thenReturn(Optional.of(event));
        useCase.receive(file, new VideoTriggerContext("audio-event", null, TriggerType.AUDIO, null, time.toString()));
        assertEquals(prediction, event.getVisionPrediction());
        assertEquals("COMPLETED", event.getStatus());
        assertNull(event.getRiskLevel());
        assertNull(event.getRiskScore());
        verifyNoInteractions(agent);
    }

    @Test void audioVisionEventsViolateTriggerSpecificContract() {
        stubStorageAndVision(List.of(new VisionEvent("SILENT_SIGNAL", 0.8, 10, Map.of())));
        SecurityEvent event = SecurityEvent.builder().eventId("audio-event")
                .triggeredAt(time).status("CAMERA_REQUESTED").build();
        when(repository.findByEventId("audio-event")).thenReturn(Optional.of(event));
        assertThrows(BusinessException.class, () -> useCase.receive(file,
                new VideoTriggerContext("audio-event", null, TriggerType.AUDIO, null, time.toString())));
        assertEquals("FAILED", event.getStatus());
        verifyNoInteractions(agent);
    }

    @Test void keypadRequiresTriggeredAtBeforeSavingVideo() {
        assertThrows(BusinessException.class, () -> useCase.receive(file,
                new VideoTriggerContext(null, "trg-keypad", TriggerType.KEYPAD, null, null)));
        verifyNoInteractions(storage, visionPort, repository, agent);
    }

    private void stubStorageAndVision(List<VisionEvent> events) {
        stubStorageAndVision("A18", events);
    }

    private void stubStorageAndVision(String prediction, List<VisionEvent> events) {
        when(storage.saveVideo(anyString(), any())).thenReturn(new MediaStoragePort.StoredMedia("v.mp4", "video-path"));
        when(visionPort.analyze(any(), any())).thenAnswer(invocation -> {
            VideoTriggerContext context = invocation.getArgument(1);
            return new VisionAnalysis(
                context.triggerId(), context.triggerType(), context.securityEventId(), time, time.plusSeconds(10),
                new VisionVideoInfo("v.mp4", 10.0, 30.0, 300),
                context.triggerType() == TriggerType.AUDIO
                        ? new VisionBehavior(prediction, "behavior", 0.46, Map.of(prediction, 0.46)) : null,
                events, new VisionObservations(1, true, true));
        });
    }

    private SecurityEvent savedEvent() {
        var captor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
