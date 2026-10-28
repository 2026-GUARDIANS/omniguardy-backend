CREATE TABLE video_uploads (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    trigger_id VARCHAR(128) NOT NULL, trigger_type VARCHAR(20) NOT NULL,
    triggered_at VARCHAR(64), content_sha256 CHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL, content_type VARCHAR(255), size_bytes BIGINT NOT NULL,
    user_id BIGINT, security_event_id VARCHAR(36),
    object_key VARCHAR(500), stored_filename VARCHAR(255),
    vision_json MEDIUMTEXT, response_json MEDIUMTEXT,
    running BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_video_uploads_user (user_id),
    CONSTRAINT fk_video_upload_user FOREIGN KEY (user_id) REFERENCES users(id)
);
