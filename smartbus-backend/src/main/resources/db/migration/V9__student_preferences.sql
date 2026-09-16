-- V9: Student Route & Bus Preferences
ALTER TABLE students ADD COLUMN IF NOT EXISTS preferred_route_id UUID REFERENCES routes(id) ON DELETE SET NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS preferred_bus_id UUID REFERENCES buses(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_students_preferred_route ON students(preferred_route_id);
CREATE INDEX IF NOT EXISTS idx_students_preferred_bus ON students(preferred_bus_id);
