# SmartBus AI - Project Audit Report

This audit documents the existing system state, identifies missing requirements for **Phase 1: Admin Portal**, and establishes a recommended implementation path.

---

## 1. Existing Architecture & Technology Stack

### Backend Architecture
The backend is structured using a hybrid **Clean / Hexagonal Architecture**:
- **Domain Layer (`com.smartbus.domain.model`)**: Represents core data entities and validation rules, independent of frameworks.
- **Application Layer (`com.smartbus.application`)**: Exposes inbound/outbound Ports (`port/in`, `port/out`) and core Service Use Cases (`service`).
- **Infrastructure Layer (`com.smartbus.infrastructure`)**: Implements adapters including Spring MVC Controllers, JPA Repository Adapters, and custom configurations.
- **Security & WebSocket (`com.smartbus.security`, `com.smartbus.websocket`)**: Manages JWT authentication and real-time live coordinate stream handlers.

### Technologies
- **Backend**: Java 21, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Hibernate 6.4, Flyway 9.22, JWT, H2 (for local development/testing), PostgreSQL (for production/dockerized profile).
- **Frontend**: React 18, Vite 5.2, TypeScript, Tailwind CSS 3.4, Material UI 5.15, React Router DOM 6.22, Recharts 2.12, Leaflet 1.9, Axios.

---

## 2. Existing Database Schema & Entities

The following tables are created via Flyway migrations `V1__init_schema.sql` and `V2__seed_data.sql`:
1. **`users`**: Core user accounts with role designations (`SUPER_ADMIN`, `ADMIN`, `DRIVER`, `STUDENT`, `TRANSPORT_MANAGER`, `SECURITY`).
2. **`students`**: Sub-profile for students, linked to `users`.
3. **`drivers`**: Sub-profile for drivers, linked to `users` with licensing fields.
4. **`buses`**: Vehicle inventory tracking status, lat/long, and basic metadata.
5. **`routes`**: Path descriptors with starting/ending nodes.
6. **`stops`**: Stop coordinates (latitude/longitude) and metadata.
7. **`route_stops`**: Junction table mapping Stops to Routes with sequence sequences.
8. **`schedules`**: Connects Route + Bus + Driver + Times (days of week).
9. **`trips`**: Live transit run representations (status: `SCHEDULED`, `EN_ROUTE`, `PAUSED`, `COMPLETED`, `CANCELLED`).
10. **`gps_locations`**: Historical GPS coordinate logs for active trips.
11. **`attendance`**: QR onboarding student logs.
12. **`notifications`**: Targeted and broadcast notification storage.
13. **`settings`**: Key-value system configurations (H2 keyword compatibility overrides implemented).
14. **`emergency_logs`**: Emergency SOS logs.
15. **`audit_logs`**: General logs.
16. **`refresh_tokens`**: Offline token tracking.
17. **`complaints` & `feedback` & `maintenance` & `passenger_counts`**: Secondary management data.

---

## 3. Existing APIs

- **Authentication (`/api/auth/**`)**:
  - `POST /api/auth/register` (user registration)
  - `POST /api/auth/login` (JWT + Refresh token generation)
  - `POST /api/auth/refresh` (refresh token exchange)
- **Buses (`/api/buses/**`)**: Basic GET/POST/PUT/DELETE operations.
- **Routes (`/api/routes/**`)**: Basic GET/POST/PUT/DELETE operations.
- **Trips & GPS (`/api/trips/**`)**: GPS update submissions, trip starts/ends, SOS pings.
- **WebSocket (`/ws/live`)**: Live coordinate tracking.

---

## 4. Existing Frontend Pages

- **`Login.tsx`**: Login/Registration UI with "Quick Demo Login" buttons.
- **`StudentDashboard.tsx`**: Passenger live map tracking, ETAs, and notification sidebars.
- **`DriverDashboard.tsx`**: Active trip selector, live coordinate simulator, and SOS triggers.
- **`AdminDashboard.tsx`**: Primary admin page with route overview, map widgets, and basic data tables.

---

## 5. Missing Functionality for Phase 1 (Admin Portal)

### Admin Authentication
- Existing auth maps standard JWTs but does not enforce strict `/api/admin/**` endpoint routing authorizations.
- Backend must validate role permissions explicitly (e.g. `@PreAuthorize("hasRole('ADMIN')")` or SecurityConfig rule mapping).

### Bus Management
- **Missing Fields**: `registrationNumber`, `manufacturer`, `manufacturingYear`, `busType`, `gpsDeviceId`, `gpsDeviceStatus`.
- **Missing Features**: Public Bus Code generation (`SB-BUS-XXXXXX`), secure QR token generation and validation, printable QR bus cards.

### Driver Management
- **Missing Fields**: `employeeId`, `licenseExpiry`, `emergencyContact`, `approvalStatus` (`PENDING`, `APPROVED`, `REJECTED`).
- **Missing Constraints**: Prevent unapproved drivers from being assigned to schedules.

### Route & Stop Management
- **Missing Fields**: Route `status` (ACTIVE/INACTIVE); Stop `expectedArrivalTime`, `expectedDepartureTime`.
- **Missing Logic**: Stop reordering sequence recalculation, route deletion safety checks (reject if referenced by active schedules).
- **Missing UI**: Full Route Map Editor with Leaflet letting admins click to place stops, drag-and-drop stops list, and render path lines.

### Schedule & Bus Assignment
- **Missing Fields**: Schedule `startDate`, `endDate`, `status` (`ACTIVE`, `INACTIVE`, `CANCELLED`).
- **Missing Validation Constraints**: Prevent overlapping schedules for the same driver or same bus. Prevent inactive bus/route/driver scheduling.
- **Missing UI**: Assignment wizard layout (Bus → Route → Driver → Schedule → Summary → Confirm).

### Audit Logging
- **Missing Logic**: Explicit capture and persistence of administrative mutations (created/deactivated bus, assigned driver, changed route).

### Admin Tables & API Structure
- Existing APIs do not implement backend-driven server pagination, filtering, and sorting.
- Admin APIs must be separated under `/api/admin/...` namespaces.

---

## 6. Problems Discovered

1. **Schema Integrity**: Database tables lack standard admin fields (e.g., public bus code, QR token registry, driver employee ID, schedule dates) which will trigger schema errors if not added.
2. **PostgreSQL/H2 Types**: Hibernate DDL validation on startup needs to be kept in sync with Flyway.
3. **Admin UI Routing**: Current `AdminDashboard` is a single unified view. It must be refactored into a full-scale Portal with a professional layout, sidebar navigation, and dedicated pages for each management module.

---

## 7. Recommended Implementation Order

1. **Step 1: Database Migration**: Apply a new Flyway migration script to add missing fields and constraints.
2. **Step 2: Backend Domain & Entities Update**: Add new properties to model classes, DTOs, and JPA repositories.
3. **Step 3: Core Business Logic (Services)**:
   - Unique Bus Code generator (Secure Random base-36/hex).
   - QR Code token registry and validator.
   - Schedule overlap and status validations.
   - Audit logging helper hook.
4. **Step 4: Admin API Controller Layer**: Build `/api/admin/...` controllers supporting backend pagination, sorting, and filtering.
5. **Step 5: Frontend Layout & Sidebar Refactor**: Create a modular Admin Portal navigation layout.
6. **Step 6: Frontend Management Modules**: Build dedicated table-views for Buses, Drivers, Routes, Stops, Schedules, and Assignments.
7. **Step 7: Route Map Editor**: Implement the visual Leaflet map click-and-drag route stop editor.
8. **Step 8: Demo Data & UI Credentials**: Expand seeder to load new entity structures and mount credentials onto the login console.
9. **Step 9: Test Suites & Verification**: Create/run Maven unit tests verifying schedule overlaps, duplicate validations, and UI builds.
