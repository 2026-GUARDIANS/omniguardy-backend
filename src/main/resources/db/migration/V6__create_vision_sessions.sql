-- Session identifiers are opaque and case-sensitive. Existing event_id collation is
-- inherited for the FK; keypad IDs and session IDs use an explicit binary collation.
CREATE TABLE keypad_camera_triggers (
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    triggered_at DATETIME(6) NOT NULL
);

CREATE TABLE vision_sessions (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    event_id VARCHAR(36),
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
    status VARCHAR(20) NOT NULL,
    registered_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_vision_session_event UNIQUE (event_id),
    CONSTRAINT uq_vision_session_trigger UNIQUE (trigger_id),
    CONSTRAINT fk_vision_session_event FOREIGN KEY (event_id) REFERENCES security_events(event_id),
    CONSTRAINT fk_vision_session_keypad FOREIGN KEY (trigger_id) REFERENCES keypad_camera_triggers(trigger_id),
    CONSTRAINT ck_vision_session_binding CHECK (
        (trigger_type = 'AUDIO' AND event_id IS NOT NULL AND trigger_id IS NULL) OR
        (trigger_type = 'KEYPAD' AND event_id IS NULL AND trigger_id IS NOT NULL)
    ),
    CONSTRAINT ck_vision_session_status CHECK (status IN ('REGISTERED', 'COMPLETED', 'FAILED'))
);
