-- Settings for Phase 5: Intelligent ETA and Smart Notification Engine

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('ETA_DELAY_THRESHOLD_MINUTES', '5', 'Threshold in minutes to flag a trip or stop as delayed');

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('ROUTE_DEVIATION_THRESHOLD_METERS', '500.0', 'Distance threshold in meters from route path to flag off-route deviation');

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('GPS_STALE_THRESHOLD_SECONDS', '60', 'Time in seconds without GPS updates before marking telemetry stale');

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('DEFAULT_AVERAGE_SPEED_KMH', '30.0', 'Default average transit speed in km/h used for ETA fallback');

INSERT INTO settings (setting_key, setting_value, description) 
VALUES ('MIN_CONSECUTIVE_DEVIATIONS_FOR_ALERT', '3', 'Number of consecutive GPS updates outside threshold required before triggering route deviation alert');
