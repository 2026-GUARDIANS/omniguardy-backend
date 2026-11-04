package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class JdbcSecurityResponseLedger implements SecurityResponseLedger {
    private final JdbcTemplate jdbc;

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueEarly(String sessionId) {
        // Only committed, validated EARLY_EVENT records are eligible. Keep the original receipt time.
        var sources = jdbc.query("""
                SELECT e.security_event_id AS event_id, e.trigger_id, e.trigger_type, e.received_at
                FROM vision_early_events e JOIN vision_sessions s ON s.session_id=e.session_id
                WHERE e.session_id=? AND e.event_type='SILENT_SIGNAL' AND e.message_type='EARLY_EVENT'
                  AND e.provisional=TRUE AND e.trigger_type=s.trigger_type
                  AND ((e.trigger_type='AUDIO' AND e.security_event_id=s.event_id)
                    OR (e.trigger_type='KEYPAD' AND e.security_event_id IS NULL AND e.trigger_id=s.trigger_id))
                ORDER BY e.received_at, e.id LIMIT 1
                """, (rs, row) -> new Binding(rs.getString("trigger_type") + ":" + rs.getString("trigger_id"),
                rs.getString("event_id"), rs.getString("trigger_id"), sessionId, rs.getString("trigger_type"),
                rs.getObject("received_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), sessionId);
        if (sources.isEmpty()) return;
        var b = sources.getFirst();
        ensure(b);
        lock(b.key());
        jdbc.update("""
                UPDATE security_response_ledger SET early_received=TRUE,
                  session_id=COALESCE(session_id,?), received_at=COALESCE(received_at,?), updated_at=?,
                  fcm_state=CASE WHEN fcm_level<3 THEN 'PENDING' ELSE fcm_state END,
                  fcm_token=CASE WHEN fcm_level<3 THEN NULL ELSE fcm_token END,
                  fcm_level=CASE WHEN fcm_level<3 THEN 3 ELSE fcm_level END,
                  sound_state=CASE WHEN sound_level<3 THEN 'PENDING' ELSE sound_state END,
                  sound_token=CASE WHEN sound_level<3 THEN NULL ELSE sound_token END,
                  sound_level=CASE WHEN sound_level<3 THEN 3 ELSE sound_level END
                WHERE response_key=? AND early_received=FALSE
                """, sessionId, utc(b.receivedAt()), now(), b.key());
    }

    @Override public List<String> undispatchedSessions(int limit) {
        return jdbc.query("""
                SELECT e.session_id FROM vision_early_events e
                LEFT JOIN security_response_ledger r ON r.trigger_type=e.trigger_type AND r.trigger_id=e.trigger_id
                WHERE r.response_key IS NULL OR r.early_received=FALSE
                GROUP BY e.session_id ORDER BY MIN(e.id) LIMIT ?
                """, (rs, row) -> rs.getString(1), limit);
    }

    @Override public List<String> pending(Channel channel, int limit) {
        return jdbc.query("SELECT response_key FROM security_response_ledger WHERE " + prefix(channel)
                + "_state='PENDING' ORDER BY updated_at, response_key LIMIT ?", (rs, row) -> rs.getString(1), limit);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkCreatedEvents() {
        var links = jdbc.query("""
                SELECT r.response_key, MIN(e.event_id) AS event_id
                FROM security_response_ledger r JOIN security_events e
                  ON e.trigger_type=r.trigger_type AND e.trigger_id=r.trigger_id
                WHERE r.trigger_type='KEYPAD' AND r.event_id IS NULL
                GROUP BY r.response_key HAVING COUNT(*)=1 ORDER BY r.response_key LIMIT 100
                """, (rs, row) -> new String[]{rs.getString("response_key"), rs.getString("event_id")});
        for (var link : links) jdbc.update("UPDATE security_response_ledger SET event_id=? WHERE response_key=? AND event_id IS NULL",
                link[1], link[0]);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimEarly(String key, Channel channel) {
        var row = lock(key);
        if (!row.state(channel).equals("PENDING")) return Optional.empty();
        // A KEYPAD event is linked only if/when the normal final-result flow has created it.
        var b = row.binding;
        if (b.eventId() == null && "KEYPAD".equals(b.triggerType())) {
            // The legacy event column may use a case-insensitive collation; trigger IDs are opaque.
            var ids = jdbc.query("SELECT event_id, trigger_id FROM security_events WHERE trigger_type='KEYPAD' AND trigger_id=?",
                    (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, b.triggerId()).stream()
                    .filter(id -> row.binding.triggerId().equals(id[1])).map(id -> id[0]).toList();
            if (ids.size() == 1) {
                jdbc.update("UPDATE security_response_ledger SET event_id=? WHERE response_key=?", ids.getFirst(), key);
                b = new Binding(key, ids.getFirst(), b.triggerId(), b.sessionId(), b.triggerType(), b.receivedAt());
            }
        }
        return Optional.of(reserve(b, channel, row.level(channel)));
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimFinal(Binding binding, Channel channel, int level) {
        ensure(binding);
        var row = lock(binding.key());
        if (!Objects.equals(row.binding.triggerId(), binding.triggerId())
                || !Objects.equals(row.binding.triggerType(), binding.triggerType())
                || row.binding.eventId() != null && !Objects.equals(row.binding.eventId(), binding.eventId()))
            throw new IllegalStateException("Response event binding mismatch");
        jdbc.update("UPDATE security_response_ledger SET event_id=COALESCE(event_id,?) WHERE response_key=?",
                binding.eventId(), binding.key());
        if (row.level(channel) >= level && !row.state(channel).equals("SKIPPED"))
            return Optional.empty();
        return Optional.of(reserve(binding, channel, level));
    }

    private Claim reserve(Binding b, Channel channel, int level) {
        String token = UUID.randomUUID().toString();
        String p = prefix(channel);
        jdbc.update("UPDATE security_response_ledger SET " + p + "_state='CALLING', " + p + "_level=?, "
                + p + "_token=?, updated_at=? WHERE response_key=?", level, token, now(), b.key());
        return new Claim(b, channel, level, token);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finished(Claim claim, String outcome) {
        if (!Set.of("SUCCEEDED", "SKIPPED", "UNCERTAIN").contains(outcome)) throw new IllegalArgumentException("Invalid outcome");
        String p = prefix(claim.channel());
        jdbc.update("UPDATE security_response_ledger SET " + p + "_state=?, updated_at=? WHERE response_key=? AND "
                + p + "_token=? AND " + p + "_state='CALLING'", outcome, now(), claim.binding().key(), claim.token());
    }

    private void ensure(Binding b) {
        jdbc.update("""
                INSERT INTO security_response_ledger (response_key,trigger_type,trigger_id,event_id,session_id,received_at,updated_at)
                VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE response_key=response_key
                """, b.key(), b.triggerType(), b.triggerId(), b.eventId(), b.sessionId(), utc(b.receivedAt()), now());
    }

    private Row lock(String key) {
        return jdbc.queryForObject("SELECT * FROM security_response_ledger WHERE response_key=? FOR UPDATE",
                (rs, n) -> new Row(binding(rs), rs.getInt("fcm_level"), rs.getString("fcm_state"),
                        rs.getInt("sound_level"), rs.getString("sound_state")), key);
    }
    private Binding binding(ResultSet rs) throws SQLException {
        var time = rs.getObject("received_at", LocalDateTime.class);
        return new Binding(rs.getString("response_key"), rs.getString("event_id"), rs.getString("trigger_id"),
                rs.getString("session_id"), rs.getString("trigger_type"), time == null ? null : time.toInstant(ZoneOffset.UTC));
    }
    private record Row(Binding binding, int fcmLevel, String fcmState, int soundLevel, String soundState) {
        int level(Channel c) { return c == Channel.FCM ? fcmLevel : soundLevel; }
        String state(Channel c) { return c == Channel.FCM ? fcmState : soundState; }
    }
    private static String prefix(Channel channel) { return channel == Channel.FCM ? "fcm" : "sound"; }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static LocalDateTime utc(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
