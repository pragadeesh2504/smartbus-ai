-- Phase 6: Performance indexes for Fleet Analytics and Admin Command Center

CREATE INDEX IF NOT EXISTS idx_trips_start_time_status
ON trips(start_time, status);

CREATE INDEX IF NOT EXISTS idx_trips_route_start_time
ON trips(route_id, start_time);

CREATE INDEX IF NOT EXISTS idx_trips_bus_start_time
ON trips(bus_id, start_time);

CREATE INDEX IF NOT EXISTS idx_trips_driver_start_time
ON trips(driver_id, start_time);

CREATE INDEX IF NOT EXISTS idx_notifications_type_created
ON notifications(type, created_at);

CREATE INDEX IF NOT EXISTS idx_driver_notifications_type_created
ON driver_notifications(type, created_at);
