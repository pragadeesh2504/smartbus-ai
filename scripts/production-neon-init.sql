-- ==============================================================================
-- SMARTBUS AI — ONE-TIME PRODUCTION NEON SANITIZATION SCRIPT
-- ==============================================================================
-- TARGET: Production Neon PostgreSQL database ONLY.
-- TIMING: Run ONCE immediately after initial Flyway migrations (V1-V17) complete on Neon,
--         and BEFORE the first real college registers.
--
-- CAUTION: DO NOT RUN THIS SCRIPT AGAINST LOCAL DEVELOPMENT POSTGRESQL!
-- Local development test records (e.g. CIT, developer accounts) are protected by preflight,
-- but this script is intended strictly for production Neon.
--
-- PURPOSE:
-- 1. Verifies via strict PREFLIGHT assertions that ONLY the expected V2/V13 seed
--    records exist. If ANY unexpected college, user, bus, route, stop, schedule,
--    trip, or GPS device is found, it raises an exception and deletes NOTHING.
-- 2. Purges ONLY the known V2/V13 seed records:
--    - 6 legacy V2 seed users (including legacy V2 seed SuperAdmin 11111111-1111-1111-1111-111111111111)
--    - 1 legacy V13 baseline college (c011e6e0-0000-0000-0000-000000000001)
--    - 2 legacy V2 demo buses, 2 routes, 7 stops, 2 schedules, 1 student profile, 1 driver profile
-- 3. NEVER deletes any real SuperAdmin created by SuperAdminBootstrapService.
-- 4. NEVER executes an unqualified "DELETE FROM users" or "DELETE FROM colleges".
-- 5. Leaves Flyway schema history intact (versions 1..17).
-- 6. Leaves system settings intact.
-- ==============================================================================

BEGIN;

-- ==============================================================================
-- STEP 1: PREFLIGHT SAFETY CHECK
-- ==============================================================================
DO $$
DECLARE
    v_unexpected_colleges INT;
    v_unexpected_users INT;
    v_unexpected_buses INT;
    v_unexpected_routes INT;
    v_unexpected_stops INT;
    v_unexpected_schedules INT;
    v_unexpected_trips INT;
    v_unexpected_gps INT;
BEGIN
    -- Anti-localhost safety assertion
    IF (inet_server_addr() = '127.0.0.1'::inet OR inet_server_addr() = '::1'::inet OR inet_server_addr() IS NULL)
       AND current_database() = 'smartbus' THEN
        RAISE EXCEPTION 'SAFETY VIOLATION: Execution aborted. Cannot run production-neon-init.sql on local development database (smartbus@localhost)!';
    END IF;

    -- 1. Check for unexpected colleges (only V13 CIT is expected on fresh migration)
    SELECT count(*) INTO v_unexpected_colleges
    FROM colleges
    WHERE id != 'c011e6e0-0000-0000-0000-000000000001';

    IF v_unexpected_colleges > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected college(s). Production sanitization aborted to prevent data loss.', v_unexpected_colleges;
    END IF;

    -- 2. Check for unexpected users (only the 6 V2 seeds or a real platform SuperAdmin are permitted)
    SELECT count(*) INTO v_unexpected_users
    FROM users
    WHERE id NOT IN (
        '11111111-1111-1111-1111-111111111111', -- V2 seed SuperAdmin
        '22222222-2222-2222-2222-222222222222', -- V2 seed Admin
        '33333333-3333-3333-3333-333333333333', -- V2 seed Driver
        '44444444-4444-4444-4444-444444444444', -- V2 seed Student
        '55555555-5555-5555-5555-555555555555', -- V2 seed Manager
        '66666666-6666-6666-6666-666666666666'  -- V2 seed Security
    ) AND role != 'SUPER_ADMIN';

    IF v_unexpected_users > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected non-seed user(s). Production sanitization aborted to prevent data loss.', v_unexpected_users;
    END IF;

    -- 3. Check for unexpected buses (only the 2 V2 demo buses expected)
    SELECT count(*) INTO v_unexpected_buses
    FROM buses
    WHERE id NOT IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222');

    IF v_unexpected_buses > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected bus(es). Production sanitization aborted.', v_unexpected_buses;
    END IF;

    -- 4. Check for unexpected routes (only the 2 V2 demo routes expected)
    SELECT count(*) INTO v_unexpected_routes
    FROM routes
    WHERE id NOT IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222');

    IF v_unexpected_routes > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected route(s). Production sanitization aborted.', v_unexpected_routes;
    END IF;

    -- 5. Check for unexpected stops (only the 7 V2 demo stops expected)
    SELECT count(*) INTO v_unexpected_stops
    FROM stops
    WHERE id NOT IN (
        'c1111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222',
        'c3333333-3333-3333-3333-333333333333', 'c4444444-4444-4444-4444-444444444444',
        'c5555555-5555-5555-5555-555555555555', 'c6666666-6666-6666-6666-666666666666',
        'c7777777-7777-7777-7777-777777777777'
    );

    IF v_unexpected_stops > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected stop(s). Production sanitization aborted.', v_unexpected_stops;
    END IF;

    -- 6. Check for unexpected schedules (only the 2 V2 demo schedules expected)
    SELECT count(*) INTO v_unexpected_schedules
    FROM schedules
    WHERE id NOT IN ('d1111111-1111-1111-1111-111111111111', 'd2222222-2222-2222-2222-222222222222');

    IF v_unexpected_schedules > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % unexpected schedule(s). Production sanitization aborted.', v_unexpected_schedules;
    END IF;

    -- 7. Check for trips (0 trips expected on fresh migration)
    SELECT count(*) INTO v_unexpected_trips FROM trips;
    IF v_unexpected_trips > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % trip(s). Production sanitization aborted.', v_unexpected_trips;
    END IF;

    -- 8. Check for GPS devices (0 GPS devices expected on fresh migration)
    SELECT count(*) INTO v_unexpected_gps FROM gps_devices;
    IF v_unexpected_gps > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Found % GPS device(s). Production sanitization aborted.', v_unexpected_gps;
    END IF;

    RAISE NOTICE 'PREFLIGHT SAFETY CHECK PASSED: Database contains only expected fresh migration seed records.';
END $$;

-- ==============================================================================
-- STEP 2: TARGETED SANITIZATION (Purge only known V2/V13 seed records)
-- ==============================================================================

-- 1. Remove records referencing demo trips, users, or students
DELETE FROM emergency_logs 
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
) OR trip_id IN (
    SELECT id FROM trips 
    WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
       OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
);

DELETE FROM feedback 
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
) OR trip_id IN (
    SELECT id FROM trips 
    WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
       OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
);

DELETE FROM attendance 
WHERE student_id = '44444444-4444-4444-4444-444444444440' 
   OR trip_id IN (
       SELECT id FROM trips 
       WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
          OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
   )
   OR boarding_stop_id IN (
       'c1111111-1111-1111-1111-111111111111',
       'c2222222-2222-2222-2222-222222222222',
       'c3333333-3333-3333-3333-333333333333',
       'c4444444-4444-4444-4444-444444444444',
       'c5555555-5555-5555-5555-555555555555',
       'c6666666-6666-6666-6666-666666666666',
       'c7777777-7777-7777-7777-777777777777'
   );

DELETE FROM trip_locations 
WHERE trip_id IN (
    SELECT id FROM trips 
    WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
       OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
);

DELETE FROM trip_stop_events 
WHERE trip_id IN (
    SELECT id FROM trips 
    WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
       OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
) OR stop_id IN (
    'c1111111-1111-1111-1111-111111111111',
    'c2222222-2222-2222-2222-222222222222',
    'c3333333-3333-3333-3333-333333333333',
    'c4444444-4444-4444-4444-444444444444',
    'c5555555-5555-5555-5555-555555555555',
    'c6666666-6666-6666-6666-666666666666',
    'c7777777-7777-7777-7777-777777777777'
);

-- 2. Delete demo trips
DELETE FROM trips 
WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
   OR route_id IN ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222')
   OR driver_id = '33333333-3333-3333-3333-333333333330';

-- 3. Delete demo bus assignments, breakdown reports, QR tokens, maintenance, schedules, route stops
DELETE FROM bus_assignments 
WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
   OR driver_id = '33333333-3333-3333-3333-333333333330';

DELETE FROM breakdown_reports 
WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
   OR driver_id = '33333333-3333-3333-3333-333333333330';

DELETE FROM bus_qr_tokens 
WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222');

DELETE FROM maintenance 
WHERE bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222');

DELETE FROM schedules 
WHERE id IN (
    'd1111111-1111-1111-1111-111111111111',
    'd2222222-2222-2222-2222-222222222222'
) OR bus_id IN ('a1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222')
  OR driver_id = '33333333-3333-3333-3333-333333333330';

DELETE FROM route_stops 
WHERE route_id IN (
    'b1111111-1111-1111-1111-111111111111',
    'b2222222-2222-2222-2222-222222222222'
) OR stop_id IN (
    'c1111111-1111-1111-1111-111111111111',
    'c2222222-2222-2222-2222-222222222222',
    'c3333333-3333-3333-3333-333333333333',
    'c4444444-4444-4444-4444-444444444444',
    'c5555555-5555-5555-5555-555555555555',
    'c6666666-6666-6666-6666-666666666666',
    'c7777777-7777-7777-7777-777777777777'
);

-- 4. Delete demo buses, routes, and stops
DELETE FROM buses WHERE id IN (
    'a1111111-1111-1111-1111-111111111111',
    'a2222222-2222-2222-2222-222222222222'
);

DELETE FROM routes WHERE id IN (
    'b1111111-1111-1111-1111-111111111111',
    'b2222222-2222-2222-2222-222222222222'
);

DELETE FROM stops WHERE id IN (
    'c1111111-1111-1111-1111-111111111111',
    'c2222222-2222-2222-2222-222222222222',
    'c3333333-3333-3333-3333-333333333333',
    'c4444444-4444-4444-4444-444444444444',
    'c5555555-5555-5555-5555-555555555555',
    'c6666666-6666-6666-6666-666666666666',
    'c7777777-7777-7777-7777-777777777777'
);

-- 5. Delete demo user profiles, notifications, tokens, and complaints
DELETE FROM driver_notifications WHERE driver_id = '33333333-3333-3333-3333-333333333330';

DELETE FROM notifications WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

DELETE FROM complaints WHERE user_id IN (
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

DELETE FROM audit_logs WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

DELETE FROM refresh_tokens WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

DELETE FROM password_reset_tokens WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

DELETE FROM students WHERE id = '44444444-4444-4444-4444-444444444440'
   OR user_id = '44444444-4444-4444-4444-444444444444';

DELETE FROM drivers WHERE id = '33333333-3333-3333-3333-333333333330'
   OR user_id = '33333333-3333-3333-3333-333333333333';

-- 6. Purge ONLY the 6 known legacy V2 seed users (including the legacy V2 seed SuperAdmin 11111111-1111-1111-1111-111111111111)
-- NEVER deletes a real SuperAdmin created by SuperAdminBootstrapService!
DELETE FROM users WHERE id IN (
    '11111111-1111-1111-1111-111111111111', -- Legacy V2 hardcoded seed SuperAdmin (password)
    '22222222-2222-2222-2222-222222222222', -- Legacy V2 Admin (admin@smartbus.com)
    '33333333-3333-3333-3333-333333333333', -- Legacy V2 Driver (driver@smartbus.com)
    '44444444-4444-4444-4444-444444444444', -- Legacy V2 Student (student@smartbus.com)
    '55555555-5555-5555-5555-555555555555', -- Legacy V2 Manager (manager@smartbus.com)
    '66666666-6666-6666-6666-666666666666'  -- Legacy V2 Security (security@smartbus.com)
);

-- 7. Purge ONLY the baseline CIT college created by V13
DELETE FROM colleges 
WHERE id = 'c011e6e0-0000-0000-0000-000000000001';

COMMIT;

-- ==============================================================================
-- VERIFICATION QUERY (Target: SUPER_ADMIN = 1, all others = 0)
-- ==============================================================================
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
UNION ALL SELECT 'gps_devices', count(*) FROM gps_devices;
