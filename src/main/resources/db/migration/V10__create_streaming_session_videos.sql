-- MP4 storage only. No Vision/Agent/fallback execution or mutation of existing tables.
CREATE TABLE streaming_session_videos (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    security_event_id VARCHAR(36),
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(40) NOT NULL DEFAULT 'video/mp4',
    storage_type VARCHAR(10) NOT NULL,
    storage_namespace VARCHAR(500) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    upload_state VARCHAR(20) NOT NULL,
    lease_token VARCHAR(36),
    lease_until DATETIME(6),
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    stored_at DATETIME(6),
    last_error VARCHAR(64),
    CONSTRAINT fk_streaming_video_session FOREIGN KEY (session_id) REFERENCES vision_sessions(session_id),
    CONSTRAINT fk_streaming_video_event FOREIGN KEY (security_event_id) REFERENCES security_events(event_id),
    CONSTRAINT ck_streaming_video_binding CHECK (
        (trigger_type = 'AUDIO' AND security_event_id IS NOT NULL) OR
        (trigger_type = 'KEYPAD' AND security_event_id IS NULL)
    ),
    CONSTRAINT ck_streaming_video_storage CHECK (storage_type IN ('S3', 'LOCAL')),
    CONSTRAINT ck_streaming_video_size CHECK (size_bytes > 0 AND size_bytes <= 104857600),
    CONSTRAINT ck_streaming_video_state CHECK (
        (upload_state = 'UPLOADING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL AND stored_at IS NULL) OR
        (upload_state = 'STORED' AND lease_token IS NULL AND lease_until IS NULL AND stored_at IS NOT NULL) OR
        (upload_state = 'FAILED' AND lease_token IS NULL AND lease_until IS NULL AND stored_at IS NULL)
    )
);
