-- Add route geometry, start/end coordinates, and polyline to routes table
ALTER TABLE routes ADD COLUMN IF NOT EXISTS start_latitude NUMERIC(10, 8);
ALTER TABLE routes ADD COLUMN IF NOT EXISTS start_longitude NUMERIC(11, 8);
ALTER TABLE routes ADD COLUMN IF NOT EXISTS end_latitude NUMERIC(10, 8);
ALTER TABLE routes ADD COLUMN IF NOT EXISTS end_longitude NUMERIC(11, 8);
ALTER TABLE routes ADD COLUMN IF NOT EXISTS polyline TEXT;
