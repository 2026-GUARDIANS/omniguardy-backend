package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.port.in.RecordKeypadVisionInputPort;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordKeypadVisionUseCase implements RecordKeypadVisionInputPort {
    private final SecurityEventRepository repository;
    private final ObjectMapper objectMapper;
    private final DemoUserResolver demoUserResolver;

    @Override
    @Transactional
    public String record(Command command) {
        var events = objectMapper.readTree(command.visionEvents());
        if (events == null || !events.isArray()) {
            throw new IllegalArgumentException("visionEvents must be a JSON array");
        }
        if (events.isEmpty()) return null;
        if (command.triggerId() == null || command.triggerId().isBlank() || command.triggeredAt() == null) {
            throw new IllegalArgumentException("KEYPAD triggerId and triggeredAt are required");
        }
        var owner = demoUserResolver.resolve().orElse(null);
        // Stable identity prevents a retry after DB commit from creating another event.
        String eventId = UUID.nameUUIDFromBytes(("KEYPAD:" + command.triggerId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var existing = repository.findByEventId(eventId);
        if (existing.isPresent()) return existing.get().getEventId();
        SecurityEvent event = SecurityEvent.builder()
                .user(owner)
                .eventId(eventId)
                .triggerId(command.triggerId()).triggerType(EventTriggerType.KEYPAD)
                .triggeredAt(command.triggeredAt()).videoPath(command.videoPath()).build();
        event.updateVisionResult(command.prediction(), command.confidence(), command.analysisMode(), command.classProbabilities(),
                command.visionEvents(), command.personCount(), command.videoDurationSeconds(), command.analyzedAt());
        repository.save(event);
        return event.getEventId();
    }
}
