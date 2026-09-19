-- ==============================================================================
-- Flyway Migration V14: Remove Student Join Code (Replace with College Code)
-- ==============================================================================

-- Drop index on student_join_code if exists
DROP INDEX IF EXISTS idx_colleges_join_code;

-- Remove student_join_code column from colleges table
ALTER TABLE colleges DROP COLUMN IF EXISTS student_join_code;
