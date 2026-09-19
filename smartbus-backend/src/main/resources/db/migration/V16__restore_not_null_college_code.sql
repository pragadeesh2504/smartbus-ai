-- ==============================================================================
-- Flyway Migration V16: Restore NOT NULL constraint on college_code
-- ==============================================================================

-- In the native product flow, a College Transport Head / Admin self-registers
-- their college with a required College Code upfront.
-- Therefore, all future colleges must have a non-null college_code.
ALTER TABLE colleges ALTER COLUMN college_code SET NOT NULL;