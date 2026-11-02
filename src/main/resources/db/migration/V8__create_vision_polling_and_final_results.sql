-- Session lifecycle in V6 and EARLY_EVENT storage in V7 remain unchanged.
CREATE TABLE vision_polling_jobs (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    security_event_id VARCHAR(36),
    processing_state VARCHAR(30) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NOT NULL,
    next_poll_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6),
    lease_token VARCHAR(36),
    attempts INT NOT NULL DEFAULT 0,
    reason VARCHAR(64),
    last_error LONGTEXT,
    last_status_json LONGTEXT,
    finished_at DATETIME(6),
    CONSTRAINT fk_vision_poll_session FOREIGN KEY (session_id) REFERENCES vision_sessions(session_id),
    CONSTRAINT ck_vision_poll_state CHECK (processing_state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FALLBACK_REQUIRED')),
    CONSTRAINT ck_vision_poll_binding CHECK (
        (trigger_type = 'AUDIO' AND security_event_id IS NOT NULL) OR
        (trigger_type = 'KEYPAD' AND security_event_id IS NULL)
    ),
    CONSTRAINT ck_vision_poll_lease CHECK (
        (processing_state = 'RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR
        (processing_state <> 'RUNNING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    INDEX ix_vision_poll_due (processing_state, next_poll_at),
    INDEX ix_vision_poll_lease (processing_state, lease_until)
);

CREATE TABLE vision_final_results (
    session_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    analysis_state VARCHAR(20) NOT NULL,
    analysis_completeness VARCHAR(40) NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    eligible_for_agent BOOLEAN NOT NULL,
    fallback_required BOOLEAN NOT NULL,
    tracked_person_count INT,
    vision_events_json LONGTEXT NOT NULL,
    related_early_event_ids_json LONGTEXT NOT NULL,
    raw_result_json LONGTEXT NOT NULL,
    emitted_at VARCHAR(64) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_vision_final_job FOREIGN KEY (session_id) REFERENCES vision_polling_jobs(session_id),
    CONSTRAINT ck_vision_final_outcome CHECK (outcome IN ('VALID', 'PARTIAL', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_vision_final_eligibility CHECK (
        (outcome = 'VALID' AND eligible_for_agent = TRUE AND fallback_required = FALSE
         AND analysis_state = 'COMPLETED' AND result_status = 'success') OR
        (outcome <> 'VALID' AND eligible_for_agent = FALSE AND fallback_required = TRUE)
    )
);
