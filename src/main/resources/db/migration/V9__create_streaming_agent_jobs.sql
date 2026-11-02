-- Independent consumer of committed V8 results. No external call runs in the V8 transaction.
CREATE TABLE streaming_agent_jobs (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    event_id VARCHAR(36),
    processing_state VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    agent_attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_token VARCHAR(36),
    lease_until DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    reason VARCHAR(64),
    last_error VARCHAR(128),
    response_state VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    fallback_required BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_streaming_agent_event UNIQUE (event_id),
    CONSTRAINT fk_streaming_agent_final FOREIGN KEY (session_id) REFERENCES vision_final_results(session_id),
    CONSTRAINT fk_streaming_agent_event FOREIGN KEY (event_id) REFERENCES security_events(event_id),
    CONSTRAINT ck_streaming_agent_state CHECK (processing_state IN
        ('PENDING', 'PREPARING', 'CALLING', 'SUCCEEDED', 'SKIPPED', 'BLOCKED', 'FAILED', 'UNCERTAIN')),
    CONSTRAINT ck_streaming_agent_attempts CHECK (attempts >= 0 AND agent_attempts >= 0),
    INDEX ix_streaming_agent_due (processing_state, next_attempt_at),
    INDEX ix_streaming_agent_lease (processing_state, lease_until)
);
