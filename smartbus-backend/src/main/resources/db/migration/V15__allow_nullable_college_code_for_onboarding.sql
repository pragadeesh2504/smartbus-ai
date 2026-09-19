-- ==============================================================================
-- Flyway Migration V15: Allow Nullable College Code for SuperAdmin Onboarding
-- ==============================================================================

-- Allow college_code to be NULL initially when a SuperAdmin onboards a college.
-- The College Admin will configure and set their own College Code upon logging in.
ALTER TABLE colleges ALTER COLUMN college_code DROP NOT NULL;
