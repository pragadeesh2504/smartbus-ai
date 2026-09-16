-- Password for all seed users is 'password' (BCrypt hash)
-- HASH: $2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq

-- Insert Users
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, role, is_active)
VALUES 
('11111111-1111-1111-1111-111111111111', 'superadmin@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'Super', 'Admin', '+919999999991', 'SUPER_ADMIN', true),
('22222222-2222-2222-2222-222222222222', 'admin@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'College', 'Admin', '+919999999992', 'ADMIN', true),
('33333333-3333-3333-3333-333333333333', 'driver@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'Ramesh', 'Kumar', '+919999999993', 'DRIVER', true),
('44444444-4444-4444-4444-444444444444', 'student@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'Rahul', 'Sharma', '+919999999994', 'STUDENT', true),
('55555555-5555-5555-5555-555555555555', 'manager@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'Vikram', 'Singh', '+919999999995', 'TRANSPORT_MANAGER', true),
('66666666-6666-6666-6666-666666666666', 'security@smartbus.com', '$2a$10$E2UPv7arKADxZc9e4VCoE.UeE7m0zE7v/pEqO5rZ/YvG40H4s7Qeq', 'Amit', 'Patel', '+919999999996', 'SECURITY', true);

-- Insert Student Profile
INSERT INTO students (id, user_id, student_id, department, batch)
VALUES ('44444444-4444-4444-4444-444444444440', '44444444-4444-4444-4444-444444444444', 'CS2023045', 'Computer Science', '2023-2027');

-- Insert Driver Profile
INSERT INTO drivers (id, user_id, license_number, is_approved, average_rating, status)
VALUES ('33333333-3333-3333-3333-333333333330', '33333333-3333-3333-3333-333333333333', 'DL-04-2015009876', true, 4.85, 'AVAILABLE');

-- Insert Buses
INSERT INTO buses (id, bus_number, model, capacity, status, current_latitude, current_longitude)
VALUES 
('a1111111-1111-1111-1111-111111111111', 'KA-01-FC-4321', 'Tata Starbus 50S', 50, 'ACTIVE', 12.971598, 77.594562),
('a2222222-2222-2222-2222-222222222222', 'KA-01-FC-8765', 'Ashok Leyland Oyster', 60, 'ACTIVE', 12.950000, 77.570000);

-- Insert Routes
INSERT INTO routes (id, route_name, start_point, end_point, distance, estimated_duration_mins)
VALUES 
('b1111111-1111-1111-1111-111111111111', 'Campus Loop Route', 'Campus Main Gate', 'Campus Main Gate', 3.50, 20),
('b2222222-2222-2222-2222-222222222222', 'City Metro Shuttle', 'Metro Station', 'Campus Main Gate', 12.00, 45);

-- Insert Stops
INSERT INTO stops (id, stop_name, latitude, longitude)
VALUES 
('c1111111-1111-1111-1111-111111111111', 'Campus Main Gate', 12.971598, 77.594562),
('c2222222-2222-2222-2222-222222222222', 'Academic Block A', 12.973000, 77.595000),
('c3333333-3333-3333-3333-333333333333', 'Science Lab Block B', 12.974000, 77.596000),
('c4444444-4444-4444-4444-444444444444', 'Central Library', 12.975000, 77.597000),
('c5555555-5555-5555-5555-555555555555', 'Boys Hostel Gate', 12.976000, 77.598000),
('c6666666-6666-6666-6666-666666666666', 'Town Square Junction', 12.960000, 77.580000),
('c7777777-7777-7777-7777-777777777777', 'Metro Station Terminal', 12.950000, 77.570000);

-- Insert Route Stops (Campus Loop Route)
INSERT INTO route_stops (route_id, stop_id, sequence_number, distance_from_start, duration_from_start_mins)
VALUES 
('b1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 1, 0.00, 0),
('b1111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222', 2, 0.60, 4),
('b1111111-1111-1111-1111-111111111111', 'c3333333-3333-3333-3333-333333333333', 3, 1.20, 8),
('b1111111-1111-1111-1111-111111111111', 'c4444444-4444-4444-4444-444444444444', 4, 1.80, 12),
('b1111111-1111-1111-1111-111111111111', 'c5555555-5555-5555-5555-555555555555', 5, 2.50, 16),
('b1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 6, 3.50, 20);

-- Insert Route Stops (City Metro Shuttle)
INSERT INTO route_stops (route_id, stop_id, sequence_number, distance_from_start, duration_from_start_mins)
VALUES 
('b2222222-2222-2222-2222-222222222222', 'c7777777-7777-7777-7777-777777777777', 1, 0.00, 0),
('b2222222-2222-2222-2222-222222222222', 'c6666666-6666-6666-6666-666666666666', 2, 6.50, 25),
('b2222222-2222-2222-2222-222222222222', 'c1111111-1111-1111-1111-111111111111', 3, 12.00, 45);

-- Insert Schedules
INSERT INTO schedules (id, route_id, bus_id, driver_id, departure_time, arrival_time, days_of_week)
VALUES 
('d1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333330', '08:30:00', '08:50:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY'),
('d2222222-2222-2222-2222-222222222222', 'b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333330', '09:00:00', '09:45:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY');

-- Insert Default Settings
INSERT INTO settings (setting_key, setting_value, description)
VALUES 
('system_name', 'SmartBus AI', 'Name of the university smart tracking platform'),
('gps_refresh_interval_seconds', '4', 'Frequency of GPS ping updates'),
('sos_alert_contact', '+919999999112', 'Admin emergency response contact');
