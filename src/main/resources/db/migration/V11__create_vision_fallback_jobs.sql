-- Independent fallback history; V8 and V9 originals are never rewritten.
CREATE TABLE vision_fallback_jobs (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    processing_state VARCHAR(24) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    vision_attempts INT NOT NULL DEFAULT 0,
    agent_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    video_deadline_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_token VARCHAR(36),
    lease_until DATETIME(6),
    video_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,
    video_storage_type VARCHAR(10),
    video_namespace VARCHAR(500),
    video_path VARCHAR(1000),
    vision_json LONGTEXT,
    event_id VARCHAR(36),
    assessment_required BOOLEAN,
    response_state VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    last_error VARCHAR(128),
    finished_at DATETIME(6),
    CONSTRAINT fk_fallback_session FOREIGN KEY (session_id) REFERENCES vision_sessions(session_id),
    CONSTRAINT fk_fallback_event FOREIGN KEY (event_id) REFERENCES security_events(event_id),
    CONSTRAINT ck_fallback_state CHECK (processing_state IN
        ('PENDING','WAITING_VIDEO','RUNNING','PREPARED','CALLING','SUCCEEDED','SKIPPED','FAILED','UNCERTAIN')),
    CONSTRAINT ck_fallback_attempts CHECK (attempts >= 0 AND vision_attempts >= 0 AND agent_attempts BETWEEN 0 AND 1),
    CONSTRAINT ck_fallback_lease CHECK (
        (processing_state IN ('RUNNING','PREPARED','CALLING') AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (processing_state NOT IN ('RUNNING','PREPARED','CALLING') AND lease_until IS NULL)),
    INDEX ix_fallback_due (processing_state,next_attempt_at),
    INDEX ix_fallback_lease (processing_state,lease_until)
);

-- Event-wide exclusion used by LEGACY, V9 and fallback through the existing Agent UseCase.
-- RESERVED/CALLING are never automatically stolen after a crash.
CREATE TABLE security_agent_executions (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    execution_token VARCHAR(36) NOT NULL,
    execution_state VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_agent_execution_event FOREIGN KEY (event_id) REFERENCES security_events(event_id),
    CONSTRAINT ck_agent_execution_state CHECK (execution_state IN ('RESERVED','CALLING','SUCCEEDED','RETRYABLE','UNCERTAIN'))
);
