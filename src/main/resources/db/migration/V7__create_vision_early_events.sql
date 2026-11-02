-- EARLY_EVENT observations only. FINAL_RESULT must have its own lifecycle/storage.
CREATE TABLE vision_early_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    event_instance_id VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    security_event_id VARCHAR(36),
    trigger_type VARCHAR(20) NOT NULL,
    event_sequence BIGINT NOT NULL,
    provisional BOOLEAN NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    detected_frame BIGINT NOT NULL,
    confirmed_frame BIGINT NOT NULL,
    captured_at LONGTEXT,
    emitted_at VARCHAR(64) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_vision_early_event UNIQUE (session_id, event_instance_id),
    CONSTRAINT fk_vision_early_session FOREIGN KEY (session_id) REFERENCES vision_sessions(session_id),
    CONSTRAINT ck_vision_early_message CHECK (message_type = 'EARLY_EVENT'),
    CONSTRAINT ck_vision_early_provisional CHECK (provisional = TRUE),
    CONSTRAINT ck_vision_early_type CHECK (event_type = 'SILENT_SIGNAL'),
    CONSTRAINT ck_vision_early_binding CHECK (
        (trigger_type = 'AUDIO' AND security_event_id IS NOT NULL) OR
        (trigger_type = 'KEYPAD' AND security_event_id IS NULL)
    )
);
