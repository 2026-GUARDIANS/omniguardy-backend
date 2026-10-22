SET @security_events_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'security_events'
);

SET @vision_analysis_mode_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'security_events'
      AND column_name = 'vision_analysis_mode'
);

SET @add_vision_analysis_mode = IF(
    @security_events_exists = 1 AND @vision_analysis_mode_exists = 0,
    'ALTER TABLE security_events ADD COLUMN vision_analysis_mode VARCHAR(40) NULL AFTER vision_confidence',
    'SELECT 1'
);

PREPARE add_vision_analysis_mode_statement FROM @add_vision_analysis_mode;
EXECUTE add_vision_analysis_mode_statement;
DEALLOCATE PREPARE add_vision_analysis_mode_statement;
