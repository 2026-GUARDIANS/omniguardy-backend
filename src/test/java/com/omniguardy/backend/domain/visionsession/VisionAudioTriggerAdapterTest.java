package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.infrastructure.persistence.SecurityEventVisionAudioTriggerAdapter;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionAudioTriggerAdapterTest {
    SecurityEventRepository events = mock(SecurityEventRepository.class);
    SecurityEventVisionAudioTriggerAdapter adapter = new SecurityEventVisionAudioTriggerAdapter(events);

    @Test void readsIssuedAudioBindingEvenAfterLegacyProcessingChangesEventStatus() {
        for (String status : new String[]{"CAMERA_REQUESTED", "VISION_ANALYZED", "COMPLETED", "FAILED"}) {
            var event = SecurityEvent.builder().eventId("event").triggerId("trigger")
                    .triggerType(EventTriggerType.AUDIO).triggeredAt(OffsetDateTime.now()).status(status).build();
            when(events.findByEventId("event")).thenReturn(Optional.of(event));
            assertEquals(Optional.of("trigger"), adapter.findIssuedTriggerId("event"));
            assertEquals(status, event.getStatus());
        }
        verify(events, never()).save(any());
    }

    @Test void missingOrUnissuedOrNonAudioBindingsAreNotEligible() {
        assertTrue(adapter.findIssuedTriggerId("event").isEmpty());
        for (var event : new SecurityEvent[]{SecurityEvent.builder().eventId("other").triggerId("trigger")
                .triggerType(EventTriggerType.AUDIO).triggeredAt(OffsetDateTime.now()).build(),
                SecurityEvent.builder().eventId("event").triggerId("trigger").triggerType(EventTriggerType.KEYPAD)
                        .triggeredAt(OffsetDateTime.now()).build(),
                SecurityEvent.builder().eventId("event").triggerId("trigger").triggerType(EventTriggerType.AUDIO).build(),
                SecurityEvent.builder().eventId("event").triggerType(EventTriggerType.AUDIO).triggeredAt(OffsetDateTime.now()).build()}) {
            when(events.findByEventId("event")).thenReturn(Optional.of(event));
            assertTrue(adapter.findIssuedTriggerId("event").isEmpty());
        }
        verify(events, never()).save(any());
    }
}
