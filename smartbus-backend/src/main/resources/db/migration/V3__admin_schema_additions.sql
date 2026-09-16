-- Create gps_devices table first
CREATE TABLE IF NOT EXISTS gps_devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE NOT NULL,
    imei VARCHAR(100) UNIQUE,
    sim_identifier VARCHAR(100),
    provider VARCHAR(100),
    status VARCHAR(50) DEFAULT 'NOT_CONFIGURED' NOT NULL,
    last_seen TIMESTAMP WITH TIME ZONE,
    battery_level INTEGER,
    firmware_version VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Alter buses table
ALTER TABLE buses ADD COLUMN IF NOT EXISTS registration_number VARCHAR(50) UNIQUE;
ALTER TABLE buses ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(100);
ALTER TABLE buses ADD COLUMN IF NOT EXISTS manufacturing_year INTEGER;
ALTER TABLE buses ADD COLUMN IF NOT EXISTS bus_type VARCHAR(50);
ALTER TABLE buses ADD COLUMN IF NOT EXISTS bus_code VARCHAR(50) UNIQUE;
ALTER TABLE buses ADD COLUMN IF NOT EXISTS gps_device_id UUID REFERENCES gps_devices(id);

-- Alter drivers table
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS employee_id VARCHAR(50) UNIQUE;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS license_expiry DATE;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(50);
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'PENDING';

-- Alter routes table
ALTER TABLE routes ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE';

-- Alter route_stops table
ALTER TABLE route_stops ADD COLUMN IF NOT EXISTS expected_arrival_time TIME;
ALTER TABLE route_stops ADD COLUMN IF NOT EXISTS expected_departure_time TIME;

-- Alter schedules table
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE';

-- Create bus_qr_tokens table
CREATE TABLE IF NOT EXISTS bus_qr_tokens (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    bus_id UUID NOT NULL REFERENCES buses(id) ON DELETE CASCADE,
    token VARCHAR(255) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- Alter audit_logs table to add old/new values, entity name and ID
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_name VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_id VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;

-- Create bus_assignments table
CREATE TABLE IF NOT EXISTS bus_assignments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    bus_id UUID NOT NULL REFERENCES buses(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    schedule_id UUID NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE' NOT NULL
);
