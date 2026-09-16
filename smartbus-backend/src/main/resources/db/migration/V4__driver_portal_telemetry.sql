-- Alter trips table
ALTER TABLE trips ADD COLUMN IF NOT EXISTS start_latitude DOUBLE PRECISION;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS start_longitude DOUBLE PRECISION;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS end_latitude DOUBLE PRECISION;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS end_longitude DOUBLE PRECISION;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS distance DOUBLE PRECISION;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS duration INTEGER;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS primary_tracking_source VARCHAR(50);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS backup_tracking_source VARCHAR(50);

-- Create trip_locations table
CREATE TABLE IF NOT EXISTS trip_locations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed DOUBLE PRECISION NOT NULL,
    heading DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    tracking_source VARCHAR(50) NOT NULL,
    source_event_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for trip_locations
CREATE INDEX IF NOT EXISTS idx_trip_locations_trip_id ON trip_locations(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_locations_timestamp ON trip_locations(timestamp);
CREATE INDEX IF NOT EXISTS idx_trip_locations_trip_time ON trip_locations(trip_id, timestamp);

-- Create breakdown_reports table
CREATE TABLE IF NOT EXISTS breakdown_reports (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    bus_id UUID NOT NULL REFERENCES buses(id) ON DELETE CASCADE,
    trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
    issue_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    photo_url VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create driver_notifications table
CREATE TABLE IF NOT EXISTS driver_notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create trip_stop_events table
CREATE TABLE IF NOT EXISTS trip_stop_events (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    stop_id UUID NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Alter emergency_logs table
ALTER TABLE emergency_logs ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'OPEN' NOT NULL;
