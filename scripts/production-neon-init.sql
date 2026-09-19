-- ==============================================================================
-- SMARTBUS AI — AUTHORIZED ONE-TIME PRODUCTION NEON DATA CLEANUP
-- ==============================================================================
-- TARGET: Production Neon PostgreSQL Database ONLY
--
-- PURPOSE:
-- Safely purges all operational, demo, and tenant data from the production Neon
-- database while preserving:
--   1. Exactly the single platform-owner SUPER_ADMIN (superadmin@smartbus.com, college_id = NULL)
--   2. All database schema, tables, indexes, constraints, and sequences
--   3. Flyway migration history (flyway_schema_history)
--   4. System settings (settings table)
--
-- SAFETY & ATOMICITY:
--   - Wrapped entirely in an atomic transaction: BEGIN ... COMMIT
--   - Any assertion failure or query error triggers RAISE EXCEPTION and automatic ROLLBACK
--   - Localhost guard prevents execution against local development database
--   - Compatible with Neon SQL Editor (zero psql meta-commands)
-- ==============================================================================

BEGIN;

-- ==============================================================================
-- 1. PREFLIGHT SAFETY ASSERTIONS
-- ==============================================================================
DO $$
DECLARE
    v_server_ip inet;
    v_db_name text;
    v_super_admin_count int;
    v_super_admin_email text;
BEGIN
    v_server_ip := inet_server_addr();
    v_db_name := current_database();

    -- Assertion 1: Abort immediately if target is local development database
    IF (v_server_ip = '127.0.0.1'::inet OR v_server_ip = '::1'::inet OR v_server_ip IS NULL)
       AND v_db_name = 'smartbus' THEN
        RAISE EXCEPTION 'SAFETY ABORT: Execution blocked. Target host (%) is local development database (%). This cleanup is authorized ONLY for production Neon.', v_server_ip, v_db_name;
    END IF;

    -- Assertion 2: Verify exactly one SUPER_ADMIN user exists
    SELECT count(*) INTO v_super_admin_count FROM users WHERE role = 'SUPER_ADMIN';
    IF v_super_admin_count != 1 THEN
        RAISE EXCEPTION 'PREFLIGHT ABORT: Expected exactly 1 SUPER_ADMIN user before cleanup, found %. Cleanup aborted.', v_super_admin_count;
    END IF;

    -- Assertion 3: Verify the SUPER_ADMIN email is superadmin@smartbus.com
    SELECT email INTO v_super_admin_email FROM users WHERE role = 'SUPER_ADMIN' LIMIT 1;
    IF LOWER(v_super_admin_email) != 'superadmin@smartbus.com' THEN
        RAISE EXCEPTION 'PREFLIGHT ABORT: Expected SUPER_ADMIN email to be superadmin@smartbus.com, found %. Cleanup aborted.', v_super_admin_email;
    END IF;

    RAISE NOTICE '==================================================';
    RAISE NOTICE 'PREFLIGHT PASSED: Environment is NOT localhost.';
    RAISE NOTICE 'Found authorized platform owner: %', v_super_admin_email;
    RAISE NOTICE '==================================================';
END $$;

-- Display row counts BEFORE cleanup
SELECT '--- BEFORE CLEANUP ROW COUNTS ---' AS status;

SELECT 'colleges' AS table_name, count(*) AS count FROM colleges
UNION ALL SELECT 'users (total)', count(*) FROM users
UNION ALL SELECT 'users (SUPER_ADMIN)', count(*) FROM users WHERE role = 'SUPER_ADMIN'
UNION ALL SELECT 'users (ADMIN)', count(*) FROM users WHERE role = 'ADMIN'
UNION ALL SELECT 'users (STUDENT)', count(*) FROM users WHERE role = 'STUDENT'
UNION ALL SELECT 'users (DRIVER)', count(*) FROM users WHERE role = 'DRIVER'
UNION ALL SELECT 'students', count(*) FROM students
UNION ALL SELECT 'drivers', count(*) FROM drivers
UNION ALL SELECT 'buses', count(*) FROM buses
UNION ALL SELECT 'routes', count(*) FROM routes
UNION ALL SELECT 'stops', count(*) FROM stops
UNION ALL SELECT 'schedules', count(*) FROM schedules
UNION ALL SELECT 'trips', count(*) FROM trips
UNION ALL SELECT 'gps_devices', count(*) FROM gps_devices
UNION ALL SELECT 'bus_assignments', count(*) FROM bus_assignments
UNION ALL SELECT 'breakdown_reports', count(*) FROM breakdown_reports
UNION ALL SELECT 'maintenance', count(*) FROM maintenance
UNION ALL SELECT 'emergency_logs', count(*) FROM emergency_logs
UNION ALL SELECT 'feedback', count(*) FROM feedback
UNION ALL SELECT 'attendance', count(*) FROM attendance
UNION ALL SELECT 'complaints', count(*) FROM complaints
UNION ALL SELECT 'notifications', count(*) FROM notifications
ORDER BY table_name;

-- ==============================================================================
-- 2. PURGE OPERATIONAL AND TENANT DATA (Hierarchical FK order)
-- ==============================================================================

-- LEVEL 1: Operational events, logs, telemetry, and attendance (leaf tables)
DELETE FROM attendance;
DELETE FROM emergency_logs;
DELETE FROM feedback;
DELETE FROM trip_locations;
DELETE FROM trip_stop_events;
DELETE FROM gps_locations;
DELETE FROM passenger_counts;
DELETE FROM breakdown_reports;

-- LEVEL 2: Trips
DELETE FROM trips;

-- LEVEL 3: Schedules, assignments, maintenance, and route junctions
DELETE FROM bus_assignments;
DELETE FROM schedules;
DELETE FROM maintenance;
DELETE FROM bus_qr_tokens;
DELETE FROM student_favorite_buses;
DELETE FROM route_stops;

-- LEVEL 4: Transit infrastructure & GPS devices
DELETE FROM buses;
DELETE FROM routes;
DELETE FROM stops;
DELETE FROM gps_devices;

-- LEVEL 5: Profiles, notifications, tokens, and complaints
DELETE FROM driver_notifications;
DELETE FROM notifications;
DELETE FROM complaints;
DELETE FROM audit_logs;
DELETE FROM refresh_tokens;
DELETE FROM password_reset_tokens;
DELETE FROM students;
DELETE FROM drivers;

-- LEVEL 6: Users — Delete all accounts EXCEPT the singleton SUPER_ADMIN
DELETE FROM users WHERE role != 'SUPER_ADMIN';

-- Ensure SUPER_ADMIN college_id is NULL
UPDATE users SET college_id = NULL WHERE role = 'SUPER_ADMIN';

-- LEVEL 7: Colleges — Purge all colleges (now safe from FK constraints)
DELETE FROM colleges;

-- ==============================================================================
-- 3. POST-CLEANUP VERIFICATION ASSERTIONS (Rollback on any mismatch)
-- ==============================================================================
DO $$
DECLARE
    v_sa_count int;
    v_admin_count int;
    v_student_count int;
    v_driver_count int;
    v_college_count int;
    v_student_profile_count int;
    v_driver_profile_count int;
    v_bus_count int;
    v_route_count int;
    v_stop_count int;
    v_schedule_count int;
    v_trip_count int;
    v_gps_count int;
    v_sa_college_id uuid;
    v_sa_email text;
BEGIN
    SELECT count(*) INTO v_sa_count FROM users WHERE role = 'SUPER_ADMIN';
    SELECT count(*) INTO v_admin_count FROM users WHERE role = 'ADMIN';
    SELECT count(*) INTO v_student_count FROM users WHERE role = 'STUDENT';
    SELECT count(*) INTO v_driver_count FROM users WHERE role = 'DRIVER';
    SELECT count(*) INTO v_college_count FROM colleges;
    SELECT count(*) INTO v_student_profile_count FROM students;
    SELECT count(*) INTO v_driver_profile_count FROM drivers;
    SELECT count(*) INTO v_bus_count FROM buses;
    SELECT count(*) INTO v_route_count FROM routes;
    SELECT count(*) INTO v_stop_count FROM stops;
    SELECT count(*) INTO v_schedule_count FROM schedules;
    SELECT count(*) INTO v_trip_count FROM trips;
    SELECT count(*) INTO v_gps_count FROM gps_devices;

    IF v_sa_count != 1 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: SUPER_ADMIN count is %, expected 1. Rolling back.', v_sa_count;
    END IF;

    IF v_admin_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: ADMIN user count is %, expected 0. Rolling back.', v_admin_count;
    END IF;

    IF v_student_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: STUDENT user count is %, expected 0. Rolling back.', v_student_count;
    END IF;

    IF v_driver_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: DRIVER user count is %, expected 0. Rolling back.', v_driver_count;
    END IF;

    IF v_college_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Colleges count is %, expected 0. Rolling back.', v_college_count;
    END IF;

    IF v_student_profile_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Students table count is %, expected 0. Rolling back.', v_student_profile_count;
    END IF;

    IF v_driver_profile_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Drivers table count is %, expected 0. Rolling back.', v_driver_profile_count;
    END IF;

    IF v_bus_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Buses count is %, expected 0. Rolling back.', v_bus_count;
    END IF;

    IF v_route_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Routes count is %, expected 0. Rolling back.', v_route_count;
    END IF;

    IF v_stop_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Stops count is %, expected 0. Rolling back.', v_stop_count;
    END IF;

    IF v_schedule_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Schedules count is %, expected 0. Rolling back.', v_schedule_count;
    END IF;

    IF v_trip_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: Trips count is %, expected 0. Rolling back.', v_trip_count;
    END IF;

    IF v_gps_count != 0 THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: GPS devices count is %, expected 0. Rolling back.', v_gps_count;
    END IF;

    SELECT email, college_id INTO v_sa_email, v_sa_college_id FROM users WHERE role = 'SUPER_ADMIN' LIMIT 1;
    IF v_sa_college_id IS NOT NULL THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: SUPER_ADMIN college_id is not NULL. Rolling back.';
    END IF;

    IF LOWER(v_sa_email) != 'superadmin@smartbus.com' THEN
        RAISE EXCEPTION 'POST-CLEANUP ASSERTION FAILED: SUPER_ADMIN email is %, expected superadmin@smartbus.com. Rolling back.', v_sa_email;
    END IF;

    RAISE NOTICE '==================================================';
    RAISE NOTICE 'TRANSACTION ASSERTIONS PASSED: Target state confirmed!';
    RAISE NOTICE 'SUPER_ADMIN = 1 (%), college_id = NULL', v_sa_email;
    RAISE NOTICE 'All operational, tenant, and college records = 0';
    RAISE NOTICE '==================================================';
END $$;

COMMIT;

-- ==============================================================================
-- 4. FINAL POST-COMMIT AUDIT
-- ==============================================================================
SELECT '--- AFTER CLEANUP ROW COUNTS ---' AS status;

SELECT 'colleges' AS table_name, count(*) AS count FROM colleges
UNION ALL SELECT 'users (SUPER_ADMIN)', count(*) FROM users WHERE role = 'SUPER_ADMIN'
UNION ALL SELECT 'users (ADMIN)', count(*) FROM users WHERE role = 'ADMIN'
UNION ALL SELECT 'users (STUDENT)', count(*) FROM users WHERE role = 'STUDENT'
UNION ALL SELECT 'users (DRIVER)', count(*) FROM users WHERE role = 'DRIVER'
UNION ALL SELECT 'students', count(*) FROM students
UNION ALL SELECT 'drivers', count(*) FROM drivers
UNION ALL SELECT 'buses', count(*) FROM buses
UNION ALL SELECT 'routes', count(*) FROM routes
UNION ALL SELECT 'stops', count(*) FROM stops
UNION ALL SELECT 'schedules', count(*) FROM schedules
UNION ALL SELECT 'trips', count(*) FROM trips
UNION ALL SELECT 'gps_devices', count(*) FROM gps_devices
ORDER BY table_name;

-- Verify singleton SuperAdmin record
SELECT id, email, role, college_id, is_active FROM users WHERE role = 'SUPER_ADMIN';
