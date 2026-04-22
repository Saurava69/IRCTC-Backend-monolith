# Railway Ticket Booking System

A full-featured railway ticket booking backend (similar to IRCTC) built as a **modular monolith** with Spring Boot 3.2, demonstrating distributed systems patterns at production scale.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Framework** | Spring Boot 3.2.5, Java 17 |
| **Database** | PostgreSQL 16 + Flyway migrations |
| **Cache & Locking** | Redis 7 (distributed seat locks via Lua scripts, cache-aside, rate limiting) |
| **Search** | Elasticsearch 8.13 (CQRS read model, full-text station/train search) |
| **Messaging** | Apache Kafka (event choreography, retry topics, dead letter topics) |
| **Auth** | JWT (access + refresh tokens, role-based: USER / ADMIN) |
| **API Docs** | SpringDoc OpenAPI 3 (Swagger UI at `/swagger-ui.html`) |
| **Build** | Maven multi-module (7 modules) |
| **Infra** | Docker Compose (PostgreSQL, Redis, Kafka, Zookeeper, Elasticsearch, Kibana, Kafka UI) |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    railway-app (main)                        │
│   Spring Boot entry point, Security, Kafka, Swagger config  │
├────────┬────────┬──────────┬───────────┬───────────────────┤
│railway-│railway-│ railway- │ railway-  │ railway-          │
│ user   │ train  │ booking  │ payment   │ notification      │
│        │        │          │           │                   │
│ Auth   │Stations│ Bookings │ Payments  │ Kafka consumer    │
│ JWT    │ Trains │ Seats    │ Mock GW   │ Logs all events   │
│ Users  │ Routes │ PNR      │ Refunds   │ Retry + DLT      │
│        │ Search │ Cancel   │           │                   │
│        │ ES/CQRS│ Waitlist │           │                   │
│        │        │ Scheduler│           │                   │
├────────┴────────┴──────────┴───────────┴───────────────────┤
│                    railway-common                            │
│   Shared DTOs, events, exceptions, interfaces               │
└─────────────────────────────────────────────────────────────┘
```

## Features

### Booking Flow
- **Search** trains by station name/code and date (Elasticsearch-powered)
- **Check availability** with real-time seat counts (Redis-cached)
- **Book** with distributed seat locking (Redis Lua scripts, 10-min TTL)
- **Pay** via mock payment gateway (95% success rate simulation)
- **PNR status** with passenger-level details

### Indian Railways Model
- **Confirmed / RAC / Waitlisted** booking statuses
- Automatic **waitlist promotion** chain on cancellation (RAC -> Confirmed, Waitlisted -> RAC)
- Coach types: FIRST_AC, SECOND_AC, THIRD_AC, SLEEPER, GENERAL

### Event-Driven Architecture
```
Booking Cancel
  └─> BOOKING_CANCELLED (Kafka)
        ├─> Payment module: initiates refund
        ├─> Booking module: promotes waitlisted passengers
        └─> Notification module: logs cancellation alert
```

### Scheduled Jobs
| Job | Schedule | Purpose |
|-----|----------|---------|
| BookingCleanupJob | Every 60s | Auto-fail unpaid bookings after timeout |
| TrainRunGenerationJob | 2 AM daily | Generate train runs for next 7 days |
| SearchIndexRefreshJob | 3:30 AM daily | Nightly ES reindex safety net |
| StaleDataCleanupJob | 4 AM daily | Mark old train runs as COMPLETED |

### Admin Capabilities
- Create stations, trains, routes, schedules
- Generate train runs for date ranges
- Rebuild Elasticsearch index
- Manually trigger any scheduled job

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Run

```bash
# 1. Start infrastructure
docker compose -f docker/docker-compose.yml up -d

# 2. Wait for all services to be healthy
docker compose -f docker/docker-compose.yml ps

# 3. Build and run
mvn clean package -DskipTests
mvn spring-boot:run -pl railway-app
```

### Explore

| URL | What |
|-----|------|
| http://localhost:8080/swagger-ui.html | Swagger UI (test all APIs) |
| http://localhost:8090 | Kafka UI (topics & messages) |
| http://localhost:5601 | Kibana (Elasticsearch queries) |

### Getting Started (via Swagger UI)

1. **Register** — `POST /api/v1/auth/register`
2. **Login** — `POST /api/v1/auth/login` (copy the `accessToken`)
3. **Authorize** — Click the lock icon in Swagger UI, paste the token
4. **Create station** — `POST /api/v1/admin/stations`
5. **Create train** — `POST /api/v1/admin/trains`
6. **Create route** — `POST /api/v1/admin/routes`
7. **Create schedule** — `POST /api/v1/admin/schedules`
8. **Generate train runs** — `POST /api/v1/admin/train-runs/generate`
9. **Reindex ES** — `POST /api/v1/admin/search/reindex`
10. **Search trains** — `GET /api/v1/trains/search?from=NDLS&to=ADI&date=2026-04-22`
11. **Check availability** — `GET /api/v1/availability?trainRunId=1&coachType=SLEEPER`
12. **Book** — `POST /api/v1/bookings`
13. **Pay** — `POST /api/v1/payments/initiate`
14. **Check PNR** — `GET /api/v1/pnr/{pnr}`
15. **Cancel** — `POST /api/v1/bookings/{pnr}/cancel`

## API Endpoints

| Tag | Endpoints | Auth |
|-----|-----------|------|
| Auth | register, login, refresh | Public |
| User | get profile, get by ID | JWT |
| Stations | search, get by code, create | Public / Admin |
| Trains | list, get by number, create | Public / Admin |
| Train Search | search by route + date | Public |
| Availability | check seat availability | Public |
| Bookings | book, my bookings, get by PNR, cancel | JWT |
| PNR Status | check PNR | Public |
| Payments | initiate, get by booking, retry | JWT |
| Admin - Trains | create stations/trains/routes/schedules | Admin |
| Admin - Bookings | generate train runs | Admin |
| Admin - Search | reindex Elasticsearch | Admin |
| Admin - Scheduler | trigger scheduled jobs | Admin |

## Project Structure

```
railway-ticket-booking/
├── railway-common/          Shared DTOs, events, exceptions, interfaces
├── railway-user/            Auth (JWT), user management
├── railway-train/           Stations, trains, routes, schedules, ES search
├── railway-booking/         Bookings, seats, PNR, cancellation, schedulers
├── railway-payment/         Payments, refunds, mock gateway
├── railway-notification/    Kafka consumer for all event notifications
├── railway-app/             Spring Boot main app, configs, security
├── docker/                  Docker Compose (7 services)
└── docs/
    ├── ARCHITECTURE.md      Detailed architecture decisions
    └── LEARNINGS.md         Technology concepts and patterns guide
```

## Key Patterns Demonstrated

- **Modular Monolith** — 7 Maven modules with strict dependency rules
- **CQRS** — PostgreSQL (write) + Elasticsearch (read) for search
- **Event Choreography** — Kafka events trigger cross-module workflows (no orchestrator)
- **Distributed Locking** — Redis Lua scripts for atomic seat reservation
- **Optimistic Locking** — `@Version` on SeatInventory for concurrent booking safety
- **Dependency Inversion** — Cross-module interfaces in common, implementations in feature modules
- **Idempotency** — Redis-based deduplication for bookings and waitlist promotion
- **Retry + DLT** — `@RetryableTopic` with dead letter topics for Kafka resilience
- **Cache Patterns** — Write-through (availability), cache-aside (PNR), TTL-based eviction

## Development Phases

| Phase | What | Key Technologies |
|-------|------|-----------------|
| 1 | Auth, users, stations, trains, routes | Spring Security, JWT, JPA, Flyway |
| 2 | Booking engine, seat locking, caching | Redis (Lua scripts), optimistic locking |
| 3 | Event-driven payment flow | Kafka, event choreography, retry topics |
| 4 | CQRS search pipeline | Elasticsearch, Kafka-triggered indexing |
| 5 | Cancellation, refund, waitlist promotion | Event chain, cross-module refund, RAC model |
| 6 | Scheduled jobs | @Scheduled, ThreadPoolTaskScheduler, cron |

## License

This project is for educational purposes.
