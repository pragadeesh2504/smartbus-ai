-- Flyway migration V12: Route Progression State Machine Cursor for Active Trip Safety

-- Add current_stop_sequence column to trips table
ALTER TABLE trips ADD COLUMN IF NOT EXISTS current_stop_sequence INT DEFAULT 1;

-- Backfill any active trips with default sequence 1
UPDATE trips SET current_stop_sequence = 1 WHERE current_stop_sequence IS NULL;
