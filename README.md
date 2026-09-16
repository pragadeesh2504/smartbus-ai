# SmartBus AI - College Bus Tracking System

SmartBus AI is an enterprise-grade, real-time college bus tracking and transit intelligence platform. It enables students to track buses in real-time, drivers to broadcast live GPS telemetry with offline SQLite synchronization, and transport managers to orchestrate fleets, routes, schedules, and view AI analytics dashboards.

## System Architecture

The project is built using a clean, modular Hexagonal (Ports and Adapters) Architecture in Spring Boot and a state-of-the-art React SPA.

```
smartbus-ai/
├── .github/workflows/          # GitHub Actions CI/CD workflows
├── smartbus-backend/           # Java 21 Spring Boot 3 backend application
│   ├── src/main/java/          # Core Domain models, Ports, Adapters, Services
│   ├── src/main/resources/     # Database configurations and Flyway migrations
│   └── Dockerfile              # Multi-stage JVM runtime containerization
├── smartbus-frontend/          # React Vite SPA TypeScript application
│   ├── src/                    # Auth contexts, WS hooks, Student/Admin/Driver Dashboards
│   ├── nginx.conf              # Reverse proxy configuration
│   └── Dockerfile              # Frontend distribution image
└── smartbus-android/           # Android Native App (Java)
    └── app/                    # Foreground Services, WorkManager, SQLite caching
```

## Technology Stack

- **Backend:** Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, WebSockets, PostgreSQL, Flyway, Maven.
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, Leaflet maps, Lucide React icons.
- **Android App:** Java, Fused location updates, Foreground Service, SQLite caching, WorkManager, Retrofit.

## API Specification

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/api/auth/register` | POST | ALL | Registers student or driver profiles |
| `/api/auth/login` | POST | ALL | Secures access token & refresh tokens |
| `/api/routes` | GET | ALL | Lists all routes, stops, and schedules |
| `/api/trips/start` | POST | DRIVER | Initializes new trip session |
| `/api/trips/{tripId}/location` | POST | DRIVER | Telemetry endpoint (fallback from WS) |
| `/api/trips/{tripId}/end` | POST | DRIVER | Completes active trip session |
| `/api/complaints` | POST | STUDENT | Logs delay reports or route issues |

## Local Setup & Startup

### Prerequisites

- Docker Desktop installed
- Java 21 JDK (to build backend locally)
- Node.js 22 (to run frontend locally)

### Option 1: Docker Compose (Recommended)

To spin up the entire system (Database, Backend, Frontend, and Nginx proxy) in one command:

```bash
docker-compose up --build
```

The services will become available at:
- **Web Application Portal:** `http://localhost` (port 80)
- **REST Backend Engine:** `http://localhost:8080`

### Option 2: Local Development

1. **Start PostgreSQL Database:**
   Ensure PostgreSQL is running locally on port `5432` with a database named `smartbus_db`.

2. **Run Backend Application:**
   ```bash
   cd smartbus-backend
   mvn spring-boot:run
   ```

3. **Run Frontend Application:**
   ```bash
   cd smartbus-frontend
   npm install
   npm run dev
   ```

4. **Compile Android Application:**
   Import the `smartbus-android` folder in Android Studio and build.
