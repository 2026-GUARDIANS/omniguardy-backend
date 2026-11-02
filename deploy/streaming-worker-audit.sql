-- Read-only. Run against a reviewed V11 database/snapshot before enabling its workers.
-- No UPDATE/DELETE, job reset, lease release or external call is performed here.
SELECT version, description, success FROM flyway_schema_history
WHERE version IN ('6','7','8','9','10','11') ORDER BY installed_rank;

SELECT processing_state, COUNT(*) AS job_count, MIN(requested_at) AS oldest_request,
       SUM(deadline_at <= UTC_TIMESTAMP(6)) AS past_deadline
FROM vision_polling_jobs GROUP BY processing_state;

SELECT outcome, eligible_for_agent, fallback_required, COUNT(*) AS result_count,
       MIN(received_at) AS oldest_result
FROM vision_final_results GROUP BY outcome, eligible_for_agent, fallback_required;

-- Preliminary VALID candidates with no V9 history. Workers also revalidate binding/completeness.
SELECT COUNT(*) AS valid_results_without_agent_history, MIN(f.received_at) AS oldest_result
FROM vision_final_results f
LEFT JOIN streaming_agent_jobs a ON a.session_id=f.session_id
LEFT JOIN vision_fallback_jobs b ON b.session_id=f.session_id AND b.processing_state<>'SKIPPED'
WHERE f.eligible_for_agent=TRUE AND f.outcome='VALID' AND a.session_id IS NULL AND b.session_id IS NULL;

SELECT processing_state, fallback_required, COUNT(*) AS job_count,
       SUM(lease_until <= UTC_TIMESTAMP(6)) AS expired_leases
FROM streaming_agent_jobs GROUP BY processing_state, fallback_required;

-- Fallback candidates not yet discovered by V11, grouped by original MP4 availability.
SELECT COALESCE(v.upload_state,'NO_VIDEO') AS video_state, COUNT(*) AS candidate_count
FROM vision_sessions s
LEFT JOIN vision_polling_jobs p ON p.session_id=s.session_id
LEFT JOIN vision_final_results f ON f.session_id=s.session_id
LEFT JOIN streaming_agent_jobs a ON a.session_id=s.session_id
LEFT JOIN vision_fallback_jobs b ON b.session_id=s.session_id
LEFT JOIN streaming_session_videos v ON v.session_id=s.session_id
WHERE b.session_id IS NULL AND (
    ((p.processing_state='FALLBACK_REQUIRED' OR f.outcome IN ('PARTIAL','FAILED','REJECTED'))
      AND (a.session_id IS NULL OR (a.processing_state='BLOCKED' AND a.fallback_required=TRUE AND a.agent_attempts=0 AND a.event_id IS NULL)))
    OR (a.processing_state='BLOCKED' AND a.fallback_required=TRUE AND a.agent_attempts=0 AND a.event_id IS NULL))
GROUP BY COALESCE(v.upload_state,'NO_VIDEO');

SELECT processing_state, reason, COUNT(*) AS job_count,
       SUM(lease_until <= UTC_TIMESTAMP(6)) AS expired_leases,
       SUM(video_deadline_at <= UTC_TIMESTAMP(6)) AS past_video_deadline
FROM vision_fallback_jobs GROUP BY processing_state, reason;

SELECT execution_state, COUNT(*) AS event_count, MIN(updated_at) AS oldest_update
FROM security_agent_executions GROUP BY execution_state;

SELECT upload_state, COUNT(*) AS video_count FROM streaming_session_videos GROUP BY upload_state;
SELECT COUNT(*) AS legacy_uploads_still_running FROM video_uploads WHERE running=TRUE;
