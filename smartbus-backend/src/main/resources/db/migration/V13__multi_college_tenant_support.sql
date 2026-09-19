-- ==============================================================================
-- Flyway Migration V13: Multi-College / Multi-Tenant Support
-- ==============================================================================

-- 1. Create colleges table
CREATE TABLE IF NOT EXISTS colleges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL UNIQUE,
    college_code VARCHAR(50) NOT NULL UNIQUE,
    student_join_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    logo_url VARCHAR(500),
    contact_email VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_colleges_code ON colleges(college_code);
CREATE UNIQUE INDEX IF NOT EXISTS idx_colleges_join_code ON colleges(student_join_code);

-- 2. Bootstrap initial baseline college: Chennai Institute of Technology (CIT)
-- Uses PostgreSQL native UUIDv4 CSPRNG to generate a secure, non-predictable 8-character token
INSERT INTO colleges (id, name, college_code, student_join_code, status, contact_email)
VALUES (
    'c011e6e0-0000-0000-0000-000000000001',
    'Chennai Institute of Technology',
    'CIT',
    'CIT-' || UPPER(SUBSTR(REPLACE(gen_random_uuid()::text, '-', ''), 1, 8)),
    'ACTIVE',
    'admin@citchennai.net'
)
ON CONFLICT (id) DO NOTHING;

-- 3. Add college_id foreign key columns to tenant-owned tables
ALTER TABLE users ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE buses ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE routes ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE stops ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE students ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE bus_assignments ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE gps_devices ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE driver_notifications ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE complaints ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE maintenance ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE emergency_logs ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE breakdown_reports ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;
ALTER TABLE passenger_counts ADD COLUMN IF NOT EXISTS college_id UUID REFERENCES colleges(id) ON DELETE RESTRICT;

-- 4. Backfill existing records (if any exist) safely using relational ownership

-- 4a. Users: All existing non-SUPER_ADMIN users belong to the baseline college
UPDATE users 
SET college_id = 'c011e6e0-0000-0000-0000-000000000001' 
WHERE role != 'SUPER_ADMIN' AND college_id IS NULL;

-- Explicitly verify SUPER_ADMIN has NULL college_id (system-level)
UPDATE users 
SET college_id = NULL 
WHERE role = 'SUPER_ADMIN';

-- 4b. Primary transit entities in local dev (affects 0 rows if production is clean/empty)
UPDATE buses SET college_id = 'c011e6e0-0000-0000-0000-000000000001' WHERE college_id IS NULL;
UPDATE routes SET college_id = 'c011e6e0-0000-0000-0000-000000000001' WHERE college_id IS NULL;
UPDATE stops SET college_id = 'c011e6e0-0000-0000-0000-000000000001' WHERE college_id IS NULL;
UPDATE schedules SET college_id = 'c011e6e0-0000-0000-0000-000000000001' WHERE college_id IS NULL;
UPDATE trips SET college_id = 'c011e6e0-0000-0000-0000-000000000001' WHERE college_id IS NULL;

-- 4c. Drivers & Students: Derive from their linked User's college_id
UPDATE drivers d 
SET college_id = u.college_id 
FROM users u 
WHERE d.user_id = u.id AND d.college_id IS NULL AND u.college_id IS NOT NULL;

UPDATE students s 
SET college_id = u.college_id 
FROM users u 
WHERE s.user_id = u.id AND s.college_id IS NULL AND u.college_id IS NOT NULL;

-- 4d. Bus Assignments: Derive from Schedule's college_id
UPDATE bus_assignments ba 
SET college_id = s.college_id 
FROM schedules s 
WHERE ba.schedule_id = s.id AND ba.college_id IS NULL AND s.college_id IS NOT NULL;

-- 4e. GPS Devices: Derive from Bus's college_id (or fallback to CIT if present)
UPDATE gps_devices gd 
SET college_id = b.college_id 
FROM buses b 
WHERE b.gps_device_id = gd.id AND gd.college_id IS NULL AND b.college_id IS NOT NULL;

UPDATE gps_devices 
SET college_id = 'c011e6e0-0000-0000-0000-000000000001' 
WHERE college_id IS NULL;

-- 4f. Secondary Operational Records: Derive from parent relationships
UPDATE complaints c 
SET college_id = u.college_id 
FROM users u 
WHERE c.user_id = u.id AND c.college_id IS NULL AND u.college_id IS NOT NULL;

UPDATE feedback f 
SET college_id = u.college_id 
FROM users u 
WHERE f.user_id = u.id AND f.college_id IS NULL AND u.college_id IS NOT NULL;

UPDATE maintenance m 
SET college_id = b.college_id 
FROM buses b 
WHERE m.bus_id = b.id AND m.college_id IS NULL AND b.college_id IS NOT NULL;

UPDATE breakdown_reports br 
SET college_id = b.college_id 
FROM buses b 
WHERE br.bus_id = b.id AND br.college_id IS NULL AND b.college_id IS NOT NULL;

UPDATE emergency_logs el 
SET college_id = t.college_id 
FROM trips t 
WHERE el.trip_id = t.id AND el.college_id IS NULL AND t.college_id IS NOT NULL;

UPDATE notifications n 
SET college_id = u.college_id 
FROM users u 
WHERE n.user_id = u.id AND n.college_id IS NULL AND u.college_id IS NOT NULL;

UPDATE driver_notifications dn 
SET college_id = d.college_id 
FROM drivers d 
WHERE dn.driver_id = d.id AND dn.college_id IS NULL AND d.college_id IS NOT NULL;

UPDATE attendance a 
SET college_id = s.college_id 
FROM students s 
WHERE a.student_id = s.id AND a.college_id IS NULL AND s.college_id IS NOT NULL;

UPDATE passenger_counts pc 
SET college_id = t.college_id 
FROM trips t 
WHERE pc.trip_id = t.id AND pc.college_id IS NULL AND t.college_id IS NOT NULL;

-- 5. Convert global uniqueness constraints to tenant-scoped composite constraints
ALTER TABLE buses DROP CONSTRAINT IF EXISTS buses_bus_number_key;
ALTER TABLE buses ADD CONSTRAINT uq_buses_college_bus_number UNIQUE (college_id, bus_number);

ALTER TABLE routes DROP CONSTRAINT IF EXISTS routes_route_name_key;
ALTER TABLE routes ADD CONSTRAINT uq_routes_college_route_name UNIQUE (college_id, route_name);

ALTER TABLE stops DROP CONSTRAINT IF EXISTS stops_stop_name_key;
ALTER TABLE stops ADD CONSTRAINT uq_stops_college_stop_name UNIQUE (college_id, stop_name);

ALTER TABLE students DROP CONSTRAINT IF EXISTS students_student_id_key;
ALTER TABLE students ADD CONSTRAINT uq_students_college_student_id UNIQUE (college_id, student_id);

-- 6. Performance Indexes for multi-tenant query isolation
CREATE INDEX IF NOT EXISTS idx_buses_college ON buses(college_id);
CREATE INDEX IF NOT EXISTS idx_routes_college ON routes(college_id);
CREATE INDEX IF NOT EXISTS idx_stops_college ON stops(college_id);
CREATE INDEX IF NOT EXISTS idx_schedules_college ON schedules(college_id);
CREATE INDEX IF NOT EXISTS idx_trips_college ON trips(college_id);
CREATE INDEX IF NOT EXISTS idx_users_college ON users(college_id);
CREATE INDEX IF NOT EXISTS idx_drivers_college ON drivers(college_id);
CREATE INDEX IF NOT EXISTS idx_students_college ON students(college_id);
CREATE INDEX IF NOT EXISTS idx_bus_assignments_college ON bus_assignments(college_id);
CREATE INDEX IF NOT EXISTS idx_complaints_college ON complaints(college_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_college ON maintenance(college_id);
CREATE INDEX IF NOT EXISTS idx_notifications_college ON notifications(college_id);
