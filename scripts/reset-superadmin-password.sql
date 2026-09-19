-- ==============================================================================
-- SMARTBUS AI -- EMERGENCY SUPERADMIN PASSWORD RESET TEMPLATE
-- ==============================================================================
-- WARNING: THIS SCRIPT IS AN EMERGENCY MANUAL TEMPLATE ONLY.
--
-- OPERATOR INSTRUCTIONS:
-- 1. This script is intended solely for emergency out-of-band manual execution
--    by authorized platform administrators.
-- 2. DO NOT commit real passwords or real BCrypt hashes to Git or source control.
-- 3. The operator MUST manually generate a valid BCrypt hash for the new password
--    and replace '<BCrypt_HASH>' below before execution.
-- 4. If executed with '<BCrypt_HASH>' intact, the script will abort immediately
--    and roll back without making any changes.
-- 5. Discard local changes or reset this file after manual execution.
-- ==============================================================================

BEGIN;

-- Safety Check 1: Ensure operator replaced the placeholder
DO $$
BEGIN
    IF '<BCrypt_HASH>' = '<' || 'BCrypt_HASH>' THEN
        RAISE EXCEPTION 'ABORT: Placeholder <BCrypt_HASH> has not been replaced. Provide a valid BCrypt hash before running this script.';
    END IF;
END $$;

-- Safety Check 2: Verify exactly one SUPER_ADMIN exists with the expected email
DO $$
DECLARE
    v_sa_count int;
    v_sa_email text;
BEGIN
    SELECT count(*) INTO v_sa_count FROM users WHERE role = 'SUPER_ADMIN';
    IF v_sa_count != 1 THEN
        RAISE EXCEPTION 'ABORT: Expected exactly 1 SUPER_ADMIN user, but found %.', v_sa_count;
    END IF;

    SELECT email INTO v_sa_email FROM users WHERE role = 'SUPER_ADMIN' LIMIT 1;
    IF LOWER(v_sa_email) != 'superadmin@smartbus.com' THEN
        RAISE EXCEPTION 'ABORT: Expected SUPER_ADMIN email to be superadmin@smartbus.com, but found %.', v_sa_email;
    END IF;
END $$;

-- Update SUPER_ADMIN password hash and preserve system invariants
UPDATE users
SET password_hash = '<BCrypt_HASH>',
    college_id = NULL,
    is_active = true
WHERE role = 'SUPER_ADMIN' AND LOWER(email) = 'superadmin@smartbus.com';

-- Safety Check 3: Post-check assertion
DO $$
DECLARE
    v_sa_college_id uuid;
    v_sa_active boolean;
    v_sa_hash text;
BEGIN
    SELECT college_id, is_active, password_hash INTO v_sa_college_id, v_sa_active, v_sa_hash
    FROM users WHERE role = 'SUPER_ADMIN' AND LOWER(email) = 'superadmin@smartbus.com';

    IF v_sa_college_id IS NOT NULL THEN
        RAISE EXCEPTION 'ABORT: SUPER_ADMIN college_id must be NULL.';
    END IF;

    IF v_sa_active IS NOT TRUE THEN
        RAISE EXCEPTION 'ABORT: SUPER_ADMIN must be active.';
    END IF;

    IF v_sa_hash IS NULL OR v_sa_hash = '<' || 'BCrypt_HASH>' THEN
        RAISE EXCEPTION 'ABORT: SUPER_ADMIN password_hash is invalid or placeholder was not replaced.';
    END IF;

    RAISE NOTICE 'SUCCESS: SuperAdmin password successfully reset.';
END $$;

COMMIT;

-- Verify updated record (displaying safe hash prefix only)
SELECT id, email, role, college_id, is_active, substring(password_hash, 1, 10) AS hash_prefix
FROM users
WHERE role = 'SUPER_ADMIN';
