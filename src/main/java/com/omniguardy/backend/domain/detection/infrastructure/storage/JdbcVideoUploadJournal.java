package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Repository
@Profile("prod")
public class JdbcVideoUploadJournal implements VideoUploadJournal {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final long demoUserId;
    public JdbcVideoUploadJournal(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${omniguardy.demo.user-id}") long demoUserId) {
        this.jdbc = jdbc; this.mapper = mapper; this.demoUserId = demoUserId;
    }
    @Override public Upload begin(MediaFile file, VideoTriggerContext context) {
        String trigger = context.triggerType() == TriggerType.AUDIO
                ? (context.securityEventId() == null || context.securityEventId().isBlank()
                    ? context.eventId() : context.securityEventId()) : context.triggerId();
        if (trigger == null || trigger.isBlank()) throw conflict();
        String id = UUID.nameUUIDFromBytes((context.triggerType() + ":" + trigger)
                .getBytes(StandardCharsets.UTF_8)).toString();
        String digest = file.sha256();
        String triggeredAt = context.triggeredAt() == null ? null : java.time.OffsetDateTime.parse(context.triggeredAt()).toInstant().toString();
        Long userId;
        try {
            userId = context.triggerType() == TriggerType.AUDIO
                ? jdbc.queryForObject("SELECT user_id FROM security_events WHERE event_id = ?", Long.class, trigger)
                : jdbc.queryForObject("SELECT id FROM users WHERE id = ?", Long.class, demoUserId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missingOwnerOrEvent) {
            throw new BusinessException(context.triggerType() == TriggerType.AUDIO
                    ? com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND
                    : com.omniguardy.backend.domain.user.domain.error.UserErrorCode.USER_NOT_FOUND);
        }
        try {
            jdbc.update("INSERT INTO video_uploads (id, trigger_id, trigger_type, content_sha256, original_filename, content_type, size_bytes, user_id, triggered_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, trigger, context.triggerType().name(), digest,
                    safeName(file.originalFilename()), file.contentType(), file.size(), userId, triggeredAt);
        } catch (DuplicateKeyException alreadyExists) { /* atomic claim below handles concurrent requests */ }
        int acquired = jdbc.update("UPDATE video_uploads SET running = TRUE, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND content_sha256 = ? AND running = FALSE AND (trigger_type <> 'KEYPAD' OR triggered_at = ?)", id, digest, triggeredAt);
        if (acquired != 1) throw conflict();
        try {
            return jdbc.queryForObject("SELECT vision_json, object_key, stored_filename, response_json FROM video_uploads WHERE id = ?",
                    (rs, row) -> new Upload(id,
                            decode(rs.getString(1), VisionAnalysis.class),
                            rs.getString(2) == null ? null : new MediaStoragePort.StoredMedia(rs.getString(3), rs.getString(2)),
                            decode(rs.getString(4), VideoReceipt.class)), id);
        } catch (RuntimeException e) { jdbc.update("UPDATE video_uploads SET running = FALSE WHERE id = ?", id); throw e; }
    }
    @Override public void analyzed(Upload upload, VisionAnalysis vision) {
        jdbc.update("UPDATE video_uploads SET vision_json = ?, triggered_at = COALESCE(?, triggered_at), updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", mapper.writeValueAsString(vision), vision.triggeredAt() == null ? null : vision.triggeredAt().toInstant().toString(), upload.id());
    }
    @Override public void stored(Upload upload, MediaStoragePort.StoredMedia stored) {
        jdbc.update("UPDATE video_uploads SET object_key = ?, stored_filename = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", stored.path(), stored.filename(), upload.id());
    }
    @Override public void complete(Upload upload, VideoReceipt receipt) {
        jdbc.update("UPDATE video_uploads SET response_json = ?, security_event_id = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", mapper.writeValueAsString(receipt), receipt.eventId(), upload.id());
    }
    @Override public void release(Upload upload) {
        jdbc.update("UPDATE video_uploads SET running = FALSE, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", upload.id());
    }
    private <T> T decode(String json, Class<T> type) { return json == null ? null : mapper.readValue(json, type); }
    private BusinessException conflict() { return new BusinessException(DetectionErrorCode.VIDEO_UPLOAD_CONFLICT); }
    private String safeName(String name) {
        if (name == null) return "video";
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        return value.substring(0, Math.min(255, value.length()));
    }
}
