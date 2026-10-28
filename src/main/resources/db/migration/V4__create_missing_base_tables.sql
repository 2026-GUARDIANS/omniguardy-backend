-- V1/V2 deliberately no-op when security_events is absent. V3 creates receipts.
-- Only create missing base tables; do not rebuild or modify existing tables/data.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL,
    name VARCHAR(30) NOT NULL, phone_number VARCHAR(20) NOT NULL UNIQUE,
    refresh_token VARCHAR(512), refresh_token_expired_at DATETIME(6)
);
CREATE TABLE IF NOT EXISTS security_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    event_id VARCHAR(36) NOT NULL UNIQUE, trigger_id VARCHAR(64),
    trigger_type ENUM('AUDIO','KEYPAD'), triggered_at DATETIME(6), user_id BIGINT,
    audio_status ENUM('BACKGROUND','HANDLE','KNOCK'), audio_probability DOUBLE,
    audio_path VARCHAR(500), video_path VARCHAR(500),
    vision_prediction VARCHAR(20), vision_confidence DOUBLE, vision_analysis_mode VARCHAR(40),
    class_probabilities TEXT, vision_events TEXT, person_count INT,
    video_duration_seconds DOUBLE, vision_analyzed_at DATETIME(6),
    risk_level VARCHAR(20), risk_score INT, agent_reason VARCHAR(1000), status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_security_event_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    type ENUM('SECURITY_EVENT','RISK_ALERT','USER_CONFIRM_REQUEST','SYSTEM') NOT NULL,
    risk_level INT NOT NULL, title VARCHAR(100) NOT NULL, message VARCHAR(1000) NOT NULL,
    is_read BIT NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS device_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL, token VARCHAR(500) NOT NULL UNIQUE,
    device_type ENUM('ANDROID','IOS') NOT NULL, active BIT NOT NULL,
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);
