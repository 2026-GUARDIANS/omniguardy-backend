SET @security_events_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'security_events'
);

SET @risk_score_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'security_events'
      AND column_name = 'risk_score'
);

SET @add_risk_score = IF(
    @security_events_exists = 1 AND @risk_score_exists = 0,
    'ALTER TABLE security_events ADD COLUMN risk_score INT NULL AFTER risk_level',
    'SELECT 1'
);

PREPARE add_risk_score_statement FROM @add_risk_score;
EXECUTE add_risk_score_statement;
DEALLOCATE PREPARE add_risk_score_statement;
