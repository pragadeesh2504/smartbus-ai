# SmartBus AI - Phase 3 Audit

This document lists the detailed audit of the SmartBus AI project's models, services, controllers, and database configurations as requested for **Phase 3: Student Portal + Driver Assignment View + Live Bus Tracking + Personalized Stop Notifications**.

---

## 1. Existing Core Models Audit

### User Model (`User.java`)
* **Location**: `com.smartbus.domain.model.User`
* **Attributes**: `id`, `email`, `passwordHash`, `firstName`, `lastName`, `phoneNumber`, `role`, `isActive`, `failedLoginAttempts`, `lockTime`, `createdAt`, `updatedAt`, `deletedAt`.
* **Roles**: Defined in `Role.java` (`SUPER_ADMIN`, `ADMIN`, `DRIVER`, `STUDENT`, `TRANSPORT_MANAGER`, `SECURITY`).

### Student Model (`Student.java`)
* **Location**: `com.smartbus.domain.model.Student`
* **Current Attributes**: `id`, `user` (linked to `users`), `studentId` (unique college registration key), `department`, `batch`.

### Driver Model (`Driver.java`)
* **Location**: `com.smartbus.domain.model.Driver`
* **Attributes**: `id`, `user` (linked to `users`), `licenseNumber`, `employeeId`, `licenseExpiry`, `emergencyContact`, `approvalStatus` (`APPROVED`/`PENDING`), `isApproved`, `averageRating`, `status` (`AVAILABLE`, `ON_TRIP`, `INACTIVE`).

### Bus Model (`Bus.java`)
* **Location**: `com.smartbus.domain.model.Bus`
* **Attributes**: `id`, `busNumber`, `registrationNumber`, `manufacturer`, `manufacturingYear`, `busType` (`AC`/`NON_AC`), `busCode` (public 6-digit hash), `gpsDevice` (foreign key to `gps_devices`), `model`, `capacity`, `status` (`ACTIVE`, `MAINTENANCE`, `INACTIVE`), `currentLatitude`, `currentLongitude`, `lastUpdated`.

### Route & Stop Models
* **Route (`Route.java`)**: `id`, `routeName`, `startPoint`, `endPoint`, `distance`, `estimatedDurationMins`, `status`.
* **Stop (`Stop.java`)**: `id`, `stopName`, `latitude`, `longitude`.
* **RouteStop (`RouteStop.java`)**: Junction model mapping route sequences with `expectedArrivalTime`, `expectedDepartureTime`, `distanceFromStart`, and `durationFromStartMins`.

### Schedule Model (`Schedule.java`)
* **Location**: `com.smartbus.domain.model.Schedule`
* **Attributes**: `id`, `route`, `bus`, `driver`, `departureTime`, `arrivalTime`, `daysOfWeek` (comma-separated), `startDate`, `endDate`, `status` (`ACTIVE`/`INACTIVE`).

### Trip Model (`Trip.java`)
* **Location**: `com.smartbus.domain.model.Trip`
* **Attributes**: `id`, `schedule`, `bus`, `driver`, `route`, `status` (`SCHEDULED`, `EN_ROUTE`, `PAUSED`, `COMPLETED`, `CANCELLED`), `startTime`, `endTime`, `actualDeparture`, `actualArrival`, `startLatitude`, `startLongitude`, `endLatitude`, `endLongitude`, `distance`, `duration`, `primaryTrackingSource`, `backupTrackingSource`.

---

## 2. Existing Infrastructure & Configuration Audit

### Database Migrations
* **Location**: `src/main/resources/db/migration`
* **Files**:
  * `V1__init_schema.sql` (Core user, driver, routes, stops, schedules, trips, and audit tables).
  * `V2__seed_data.sql` (Initial development accounts).
  * `V3__admin_schema_additions.sql` (GPS devices, QR tokens, and route stops metadata).
  * `V4__driver_portal_telemetry.sql` (Trip locations, driver breakdown reports, and geofence logs).

### GPS Telemetry
* **TripLocation (`TripLocation.java`)**: Stores real-time coordinate logs (`latitude`, `longitude`, `speed`, `heading`, `accuracy`, `tracking_source`, `timestamp`).
* **HybridTrackingService (`HybridTrackingService.java`)**: Coordinates Kalman filter smoothing and dynamically selects `GPS_DEVICE` or `DRIVER_PHONE` as the active telemetry source.

### WebSocket Live Location
* **LiveLocationWebSocketHandler (`LiveLocationWebSocketHandler.java`)**: Mapped to `/ws/live`. Subscribes clients and broadcasts real-time `BUS_LOCATION_UPDATE` payload when location updates occur.

### ETA Engine
* **EtaPredictorService (`EtaPredictorService.java`)**: Calculates ETA (in minutes) based on Haversine distance, remaining segment sequence stops, average speeds, peak rush hours, and scheduled departures.

### Notification Infrastructure
* **Notification (`Notification.java`)**: General system and user notifications.
* **DriverNotification (`DriverNotification.java`)**: Specific driver alerts.

### Security Configuration
* **SecurityConfig (`SecurityConfig.java`)**: Configures password encryption, JWT authorization validation filters, and route authority mapping (`/admin/**` mapped to `ADMIN`, `/driver/**` mapped to `DRIVER`).

### API Endpoints
* **Admin APIs**: `/admin/buses`, `/admin/drivers`, `/admin/routes`, `/admin/schedules`, `/admin/assignments`, `/admin/audit-logs`, `/admin/dashboard`.
* **Driver APIs**: `/driver/dashboard`, `/driver/assignments/today`, `/driver/trips/{scheduleId}/start`, `/driver/location`, `/driver/emergency`, `/driver/breakdown`, `/driver/notifications`.

---

## 3. Gap Analysis: Missing Student Functionality

* **Database profile expansion**: Store student batch home locations (latitude/longitude/address), preferred stops, and notification preferences.
* **Favorite buses relation**: Link students with buses they subscribe to.
* **Student API controllers**:
  * Set/edit manual home locations and preferred stops.
  * Search/lookup active buses, route details, and relative ETAs.
  * Subscribe/unsubscribe to favorite buses.
  * Read/mark notifications.
* **WebSocket subscription isolation**: Allow filtering subscriptions by busId/tripId.
* **ETA facade integration**: Provide a clear interface mapping ETAs to stops.
* **Personalized notifications dispatch**: Integrate radius alerts (Approaching: 500m, Arrived: 100m, Next stop) and respect student notification settings.
* **Student Mobile-first UI**: Home dashboard, search, live Leaflet map, notifications feed, and profile editor.

---

## 4. Proposed File Modifications & Additions

### Files to Modify
* **`com.smartbus.domain.model.Student`**: Map new profile properties and favorites relation.
* **`com.smartbus.infrastructure.adapter.jpa.StudentRepository`**: Add preferred stop lookup query.
* **`com.smartbus.application.service.GeofencingService`**: Add geofence notifications mapping and multi-stage logging.
* **`com.smartbus.websocket.LiveLocationWebSocketHandler`**: Map client subscriptions to isolated tripId/busId lists.
* **`com.smartbus.config.SecurityConfig`**: Map `/student/**` authorization rules.
* **`com.smartbus.infrastructure.controller.DriverPortalController`**: Add detailed schedule assignments detail API.
* **`com.smartbus.config.DemoDataSeeder`**: Update to include comprehensive student demo records.
* **`smartbus-frontend/src/App.tsx`**: Add student page nested routes.

### Files to Create
* **`src/main/resources/db/migration/V5__student_portal.sql`**: Apply profile columns, favorite buses table, and geofence threshold settings.
* **`com.smartbus.application.service.EtaService`**: Create ETA facade interface.
* **`com.smartbus.application.service.EtaServiceImpl`**: Implement standard ETA calculation.
* **`com.smartbus.infrastructure.controller.StudentPortalController`**: Implement student profile and search endpoints.
* **`src/test/java/com/smartbus/infrastructure/controller/StudentPortalTest.java`**: Automated test suite for student auth, favorites, search, profiles, and notifications.
* **`smartbus-frontend/src/pages/student/StudentPortal.tsx`**: Main student portal mobile layouts.
* **`smartbus-frontend/src/pages/student/Dashboard.tsx`**: Interactive dashboard overview.
* **`smartbus-frontend/src/pages/student/LiveMap.tsx`**: Leaflet live bus tracker.
* **`smartbus-frontend/src/pages/student/BusDetails.tsx`**: Bus configuration and stop-by-stop sequencing.
* **`smartbus-frontend/src/pages/student/Search.tsx`**: Bus search and favorites.
* **`smartbus-frontend/src/pages/student/Notifications.tsx`**: Alerts lists.
* **`smartbus-frontend/src/pages/student/Profile.tsx`**: Geolocation home location editor.
