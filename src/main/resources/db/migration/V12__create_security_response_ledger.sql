-- Separate from Vision/Agent lifecycles. No final evidence or event status is modified.
CREATE TABLE security_response_ledger (
    response_key VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    event_id VARCHAR(36),
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
    received_at DATETIME(6),
    early_received BOOLEAN NOT NULL DEFAULT FALSE,
    fcm_level INT NOT NULL DEFAULT 0,
    fcm_state VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    fcm_token VARCHAR(36),
    sound_level INT NOT NULL DEFAULT 0,
    sound_state VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    sound_token VARCHAR(36),
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_response_trigger_type CHECK (trigger_type IN ('AUDIO','KEYPAD')),
    CONSTRAINT ck_response_fcm_level CHECK (fcm_level IN (0,2,3)),
    CONSTRAINT ck_response_sound_level CHECK (sound_level IN (0,2,3)),
    CONSTRAINT ck_response_fcm_state CHECK (fcm_state IN ('IDLE','PENDING','CALLING','SUCCEEDED','SKIPPED','UNCERTAIN')),
    CONSTRAINT ck_response_sound_state CHECK (sound_state IN ('IDLE','PENDING','CALLING','SUCCEEDED','SKIPPED','UNCERTAIN'))
) ENGINE=InnoDB;
CREATE INDEX ix_response_fcm_pending ON security_response_ledger (fcm_state, updated_at);
CREATE INDEX ix_response_sound_pending ON security_response_ledger (sound_state, updated_at);

-- Existing observations were collected under the store-only contract. Never play historical sirens on activation.
-- SKIPPED allows an ordinary future Agent response, but callback replay does not re-arm early delivery.
INSERT INTO security_response_ledger
    (response_key, trigger_type, trigger_id, event_id, session_id, received_at, early_received,
     fcm_level, fcm_state, sound_level, sound_state, updated_at)
SELECT CONCAT(trigger_type, ':', trigger_id), trigger_type, trigger_id, MAX(security_event_id),
       MIN(session_id), MIN(received_at), TRUE, 3, 'SKIPPED', 3, 'SKIPPED', UTC_TIMESTAMP(6)
FROM vision_early_events GROUP BY trigger_type, trigger_id;
