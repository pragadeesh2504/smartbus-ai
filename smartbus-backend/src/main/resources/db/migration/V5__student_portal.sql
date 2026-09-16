-- Alter students table to add profile fields
ALTER TABLE students ADD COLUMN IF NOT EXISTS register_number VARCHAR(100);
ALTER TABLE students ADD COLUMN IF NOT EXISTS home_latitude DOUBLE PRECISION;
ALTER TABLE students ADD COLUMN IF NOT EXISTS home_longitude DOUBLE PRECISION;
ALTER TABLE students ADD COLUMN IF NOT EXISTS home_address TEXT;
ALTER TABLE students ADD COLUMN IF NOT EXISTS preferred_stop_id UUID REFERENCES stops(id) ON DELETE SET NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS notification_preferences VARCHAR(255) DEFAULT 'APPROACHING,ARRIVED,DEPARTED,DELAYED';

-- Create student_favorite_buses junction table
CREATE TABLE IF NOT EXISTS student_favorite_buses (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    bus_id UUID NOT NULL REFERENCES buses(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_bus UNIQUE(student_id, bus_id)
);

-- Index for student_favorite_buses
CREATE INDEX IF NOT EXISTS idx_student_favorite_buses_student ON student_favorite_buses(student_id);

-- Seed geofencing radius configurations
INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('APPROACHING_RADIUS_METERS', '500.0', 'Geofence radius in meters for approaching notifications');

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('ARRIVAL_RADIUS_METERS', '100.0', 'Geofence radius in meters for arrival/departure notifications');
