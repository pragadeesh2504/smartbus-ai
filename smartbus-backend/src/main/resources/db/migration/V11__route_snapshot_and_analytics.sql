-- Flyway migration V11: Route Snapshot for Active Trip Safety and Route Versioning

-- Add route_snapshot column to trips table for immutable active trip navigation snapshots
ALTER TABLE trips ADD COLUMN IF NOT EXISTS route_snapshot TEXT;

-- Add version column to routes table for tracking route revisions and updates
ALTER TABLE routes ADD COLUMN IF NOT EXISTS version INT DEFAULT 1;

-- Add index on route updated_at for fast route synchronization queries
CREATE INDEX IF NOT EXISTS idx_routes_updated_at ON routes(updated_at);
