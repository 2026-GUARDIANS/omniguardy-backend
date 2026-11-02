package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoFileInfo;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVideoRepository;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.VISION_SESSION_NOT_FOUND;

@Repository @RequiredArgsConstructor
public class JdbcStreamingVideoRepository implements StreamingVideoRepository {
    private final JdbcTemplate jdbc;
    private final Clock visionStreamingClock;

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StreamingSessionVideo reserve(VisionBinding binding, StreamingVideoFileInfo file, VideoStorageTarget target, Duration lease) {
        var sessions = jdbc.query("SELECT trigger_type, event_id, trigger_id FROM vision_sessions WHERE session_id = ? FOR UPDATE",
                (rs, row) -> new VisionBinding(binding.sessionId(), VisionTriggerType.valueOf(rs.getString("trigger_type")),
                        rs.getString("trigger_id"), rs.getString("event_id")), binding.sessionId());
        if (sessions.isEmpty()) throw new BusinessException(VISION_SESSION_NOT_FOUND);
        var session = sessions.getFirst();
        if (session.triggerType() != binding.triggerType() || !Objects.equals(session.securityEventId(), binding.securityEventId())
                || (session.triggerType() == VisionTriggerType.KEYPAD && !Objects.equals(session.triggerId(), binding.triggerId())))
            throw new BusinessException(STREAMING_VIDEO_BINDING_MISMATCH);
        var now = now(); var existing = find(binding.sessionId());
        if (existing.isPresent()) {
            var saved = existing.get();
            if (!saved.binding().equals(binding)) throw new BusinessException(STREAMING_VIDEO_BINDING_MISMATCH);
            if (!saved.sha256().equals(file.sha256()) || saved.sizeBytes() != file.sizeBytes()) throw new BusinessException(STREAMING_VIDEO_CONFLICT);
            if (saved.state() == StreamingSessionVideo.State.STORED) return saved;
            if (saved.state() == StreamingSessionVideo.State.UPLOADING && saved.leaseUntil().isAfter(now))
                throw new BusinessException(STREAMING_VIDEO_IN_PROGRESS);
            if (!saved.target().equals(target)) throw new BusinessException(STREAMING_VIDEO_STORAGE_CHANGED);
            jdbc.update("""
                    UPDATE streaming_session_videos SET upload_state = 'UPLOADING', lease_token = ?, lease_until = ?,
                        attempts = attempts + 1, updated_at = ?, last_error = NULL WHERE session_id = ?
                    """, UUID.randomUUID().toString(), sql(now.plus(lease)), sql(now), binding.sessionId());
        } else {
            jdbc.update("""
                    INSERT INTO streaming_session_videos (session_id, trigger_type, trigger_id, security_event_id,
                        content_sha256, size_bytes, original_filename, storage_type, storage_namespace, stored_filename,
                        storage_path, upload_state, lease_token, lease_until, attempts, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADING', ?, ?, 1, ?, ?)
                    """, binding.sessionId(), binding.triggerType().name(), binding.triggerId(), binding.securityEventId(),
                    file.sha256(), file.sizeBytes(), file.originalFilename(), target.storageType(), target.namespace(), target.filename(),
                    target.path(), UUID.randomUUID().toString(), sql(now.plus(lease)), sql(now), sql(now));
        }
        return find(binding.sessionId()).orElseThrow();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StreamingSessionVideo complete(StreamingSessionVideo claim) {
        var now = now();
        int updated = jdbc.update("""
                UPDATE streaming_session_videos SET upload_state = 'STORED', stored_at = ?, updated_at = ?,
                    lease_token = NULL, lease_until = NULL, last_error = NULL
                WHERE session_id = ? AND upload_state = 'UPLOADING' AND lease_token = ? AND lease_until > ?
                """, sql(now), sql(now), claim.binding().sessionId(), claim.leaseToken(), sql(now));
        if (updated != 1) throw new BusinessException(STREAMING_VIDEO_IN_PROGRESS);
        return find(claim.binding().sessionId()).orElseThrow();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(StreamingSessionVideo claim, String reason) {
        jdbc.update("""
                UPDATE streaming_session_videos SET upload_state = 'FAILED', lease_token = NULL, lease_until = NULL,
                    updated_at = ?, last_error = ? WHERE session_id = ? AND upload_state = 'UPLOADING' AND lease_token = ?
                """, sql(now()), reason, claim.binding().sessionId(), claim.leaseToken());
    }

    @Override public Optional<StreamingSessionVideo> find(String id) {
        return jdbc.query("SELECT * FROM streaming_session_videos WHERE session_id = ?", this::map, id).stream().findFirst();
    }
    @Override public Optional<String> linkedEventId(String id) {
        return jdbc.query("""
                SELECT COALESCE(v.security_event_id, a.event_id, b.event_id) AS event_id FROM streaming_session_videos v
                LEFT JOIN streaming_agent_jobs a ON a.session_id = v.session_id
                LEFT JOIN vision_fallback_jobs b ON b.session_id = v.session_id
                WHERE v.session_id = ?
                """, (rs, row) -> rs.getString("event_id"), id).stream().filter(Objects::nonNull).findFirst();
    }

    private StreamingSessionVideo map(ResultSet rs, int row) throws SQLException {
        return new StreamingSessionVideo(new VisionBinding(rs.getString("session_id"), VisionTriggerType.valueOf(rs.getString("trigger_type")),
                rs.getString("trigger_id"), rs.getString("security_event_id")), rs.getString("content_sha256"), rs.getLong("size_bytes"),
                rs.getString("original_filename"), new VideoStorageTarget(rs.getString("storage_type"), rs.getString("storage_namespace"),
                rs.getString("stored_filename"), rs.getString("storage_path")), StreamingSessionVideo.State.valueOf(rs.getString("upload_state")),
                rs.getString("lease_token"), instant(rs, "lease_until"), rs.getInt("attempts"), instant(rs, "created_at"),
                instant(rs, "stored_at"), rs.getString("last_error"));
    }
    private Instant now() { return visionStreamingClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static LocalDateTime sql(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getObject(field, LocalDateTime.class); return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
