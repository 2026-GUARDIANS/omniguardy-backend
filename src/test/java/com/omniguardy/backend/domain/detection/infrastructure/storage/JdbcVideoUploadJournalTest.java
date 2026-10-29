package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.MediaStoragePort;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcVideoUploadJournalTest {
    JdbcTemplate jdbc;
    JdbcVideoUploadJournal journal;
    MediaFile file = new MediaFile("v.mp4", "video/mp4", new byte[]{1});
    VideoTriggerContext context = new VideoTriggerContext(null, "trigger", TriggerType.KEYPAD, null, "2026-10-01T00:00:00Z");
    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES (1)");
        String ddl = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V5__create_video_upload_journal.sql"));
        jdbc.execute(ddl);
        journal = new JdbcVideoUploadJournal(jdbc, new ObjectMapper(), 1L);
    }
    @Test void concurrentAndChangedContentAreRejectedAndCompletedResponseReplays() {
        var first = journal.begin(file, context);
        assertThrows(BusinessException.class, () -> journal.begin(file, context));
        var vision = new VisionAnalysis("trigger", TriggerType.KEYPAD, null, OffsetDateTime.parse(context.triggeredAt()),
                OffsetDateTime.parse(context.triggeredAt()), null, null, List.of(), null);
        journal.analyzed(first, vision);
        journal.stored(first, new MediaStoragePort.StoredMedia("v.mp4", "videos/key"));
        journal.complete(first, new VideoReceipt(null, "v.mp4", vision)); journal.release(first);
        assertThrows(BusinessException.class, () -> journal.begin(new MediaFile("v", null, new byte[]{2}), context));
        var replay = journal.begin(file, context);
        assertEquals(vision, replay.vision()); assertEquals("videos/key", replay.stored().path());
        assertNotNull(replay.completed()); journal.release(replay);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video_uploads", Integer.class));
    }
    @Test void failedAttemptCanResumeVisionCheckpoint() {
        var first = journal.begin(file, context);
        var vision = new VisionAnalysis("trigger", TriggerType.KEYPAD, null, null, null, null, null, List.of(), null);
        journal.analyzed(first, vision); journal.release(first);
        var retry = journal.begin(file, context);
        assertEquals(vision, retry.vision()); assertNull(retry.stored()); assertNull(retry.completed());
    }
}
