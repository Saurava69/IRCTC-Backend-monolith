# Railway Ticket Booking System - Architecture & Learning Guide

> This document explains **what** we built, **why** we built it that way, and the **concepts** behind every decision.
> Read this before diving into the code.

---

## Table of Contents

1. [The Big Picture](#1-the-big-picture)
2. [Why Modular Monolith (Not Microservices)](#2-why-modular-monolith-not-microservices)
3. [Maven Multi-Module Setup](#3-maven-multi-module-setup)
4. [Module Dependency Rule](#4-module-dependency-rule)
5. [The Layered Architecture Inside Each Module](#5-the-layered-architecture-inside-each-module)
6. [Database Design & Flyway Migrations](#6-database-design--flyway-migrations)
7. [Authentication System (JWT)](#7-authentication-system-jwt)
8. [Spring Security Configuration](#8-spring-security-configuration)
9. [Global Exception Handling](#9-global-exception-handling)
10. [DTO Pattern - Why Not Expose Entities](#10-dto-pattern---why-not-expose-entities)
11. [Event Envelope - Preparing for Kafka](#11-event-envelope---preparing-for-kafka)
12. [Configuration Management](#12-configuration-management)
13. [What Each Module Does](#13-what-each-module-does)
14. [The Railway Domain - Key Concepts](#14-the-railway-domain---key-concepts)
15. [Redis & Distributed Systems Patterns (Phase 2)](#15-redis--distributed-systems-patterns-phase-2)
16. [The Booking Engine — How It All Fits Together](#16-the-booking-engine--how-it-all-fits-together)
17. [Kafka & Event-Driven Architecture (Phase 3)](#17-kafka--event-driven-architecture-phase-3)
18. [The Payment Flow — End to End](#18-the-payment-flow--end-to-end)
19. [CQRS & Elasticsearch Search Pipeline (Phase 4)](#19-cqrs--elasticsearch-search-pipeline-phase-4)
20. [Cancellation, Refund & Waitlist Promotion (Phase 5)](#20-cancellation-refund--waitlist-promotion--event-choreography-phase-5)
21. [Scheduled Jobs (Phase 6)](#21-scheduled-jobs-phase-6)
22. [File Reference Guide](#22-file-reference-guide)

---

## 1. The Big Picture

We're building a **railway ticket booking backend** similar to IRCTC. The goal isn't just to make it work — it's to learn **how systems work at scale**. Every technology choice teaches a distributed systems concept:

```
                    ┌──────────────────────────────────────────────────┐
                    │              What We're Building                  │
                    ├──────────────────────────────────────────────────┤
                    │                                                  │
                    │  Spring Boot App (Modular Monolith)              │
                    │    ├── User Module (auth, profiles)              │
                    │    ├── Train Module (stations, routes, coaches)  │
                    │    ├── Booking Module (seat lock, PNR)      [P2] │
                    │    ├── Payment Module (pay, retry)          [P3] │
                    │    └── Notification Module (Kafka consumer) [P3] │
                    │                                                  │
                    ├──────────── Talks To ────────────────────────────┤
                    │                                                  │
                    │  PostgreSQL ──── Primary database (write)        │
                    │  Redis ────────── Caching, distributed locks [P2]│
                    │  Kafka ────────── Event streaming           [P3] │
                    │  Elasticsearch ── Search & read model       [P4] │
                    │                                                  │
                    │  [P1] = Phase 1, [P2] = Phase 2, [P3] = Phase 3 │
                    └──────────────────────────────────────────────────┘
```

**Phase 1 (foundation)** sets up the foundation: project structure, database schema, authentication, and CRUD APIs for users/trains/stations/routes.

**Phase 2 (booking + Redis)** adds the booking engine, seat availability, distributed locking, caching, rate limiting, and idempotency.

**Phase 3 (payments + Kafka)** introduces event-driven architecture with Kafka and a mock payment gateway. The system becomes truly async — booking, payment, and notification modules communicate via events.

**Phase 4 (search + CQRS)** adds Elasticsearch as a read-optimized store for train search. Writes stay in PostgreSQL (source of truth), Kafka events trigger an indexing pipeline that builds denormalized search documents in ES. Users get fast, full-text train search — decoupled from the write path.

---

## 2. Why Modular Monolith (Not Microservices)

### What is a Modular Monolith?

It's a **single deployable application** (one JAR file, one JVM process) that's internally split into independent modules. Each module owns its own domain (users, trains, bookings) and has clear boundaries.

### Why not microservices?

| Concern | Microservices | Modular Monolith (our choice) |
|---------|--------------|-------------------------------|
| **Deployment** | 8+ separate services, each needs its own Docker container, port, health check | One JAR, one process |
| **DevOps overhead** | Need Kubernetes, service mesh, distributed tracing, API gateway | Just Docker Compose for infra |
| **Local development** | Start 8 services to test one feature | Start one app |
| **Debugging** | Request travels across network boundaries, need correlation IDs to trace | Single stack trace, step-through debugging |
| **Data consistency** | Distributed transactions are HARD (two-phase commit, saga patterns) | Can use database transactions within the app |
| **Learning** | You spend 80% of time on infrastructure, 20% on actual patterns | You spend 80% on patterns, 20% on infrastructure |

### The key insight

A modular monolith can use **the same distributed patterns** as microservices:
- Modules communicate via **Kafka events** (not direct method calls)
- Each module **could be extracted** into its own service later — the Kafka boundary already exists
- We still learn CQRS, event-driven architecture, distributed locking — all within one process

### The discipline

The rule that makes this work: **modules depend only on `railway-common`, never on each other**. If the `railway-booking` module needs to know about a train, it receives that data through a Kafka event, not by importing `TrainRepository`.

---

## 3. Maven Multi-Module Setup

### What is Maven Multi-Module?

Maven builds your project. A **multi-module** setup means one parent project with multiple child projects (modules), each producing its own JAR. The parent manages shared configuration.

### Our structure

```
railwayTicketBooking/
├── pom.xml                    ← PARENT POM (no code, just configuration)
├── railway-common/pom.xml     ← Shared library
├── railway-user/pom.xml       ← User module
├── railway-train/pom.xml      ← Train module
├── railway-booking/pom.xml    ← Booking module (stub)
├── railway-payment/pom.xml    ← Payment module (stub)
├── railway-notification/pom.xml ← Notification module (stub)
└── railway-app/pom.xml        ← Main application (assembles everything)
```

### How the Parent POM works

**File: `pom.xml` (root)**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>            <!-- All modules inherit this Spring Boot version -->
</parent>

<modules>
    <module>railway-common</module>      <!-- Build order matters -->
    <module>railway-user</module>
    <module>railway-train</module>
    ...
    <module>railway-app</module>         <!-- Built last, depends on all others -->
</modules>
```

**Three key sections:**

1. **`<properties>`** — Version numbers defined once, used everywhere:
   ```xml
   <java.version>17</java.version>
   <jjwt.version>0.12.5</jjwt.version>
   ```

2. **`<dependencyManagement>`** — Declares available dependencies and their versions. Child modules can use these without specifying a version. This prevents version conflicts.

3. **`<build><pluginManagement>`** — Configures how code compiles. We configure annotation processors for Lombok (generates getters/setters) and MapStruct (generates DTO mappings).

### Why this matters at scale

- **Compile-time boundary enforcement**: If `railway-booking` doesn't list `railway-train` as a dependency, it literally cannot import its classes. The compiler prevents coupling.
- **Independent build**: `mvn compile -pl railway-user` compiles just the user module.
- **Selective testing**: Test just the module you changed.

---

## 4. Module Dependency Rule

```
                    ┌─────────────────┐
                    │  railway-common  │  ← Shared by everyone
                    └────────┬────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
    ┌───────┴──────┐ ┌──────┴───────┐ ┌──────┴───────┐
    │ railway-user │ │ railway-train│ │railway-booking│ ...
    └───────┬──────┘ └──────┬───────┘ └──────┬───────┘
            │                │                │
            └────────────────┼────────────────┘
                             │
                    ┌────────┴────────┐
                    │   railway-app   │  ← Assembles all modules
                    └─────────────────┘
```

**Rules:**
- Every module depends on `railway-common` (for shared DTOs, exceptions, events)
- `railway-booking` also depends on `railway-user` (for JWT authentication principal)
- Other modules do NOT cross-depend — `railway-payment` reads the `bookings` table via native SQL, not by importing booking classes
- Only `railway-app` depends on all modules (it's the assembly point)
- Cross-module communication happens through **Kafka events** (Phase 3)

**Why?** In a real railway system, the booking team and the train scheduling team are different teams. They shouldn't need to deploy together or break each other's code. This dependency rule enforces that separation at compile time.

---

## 5. The Layered Architecture Inside Each Module

Each module follows the same internal structure:

```
railway-user/
├── controller/    ← HTTP layer: receives requests, returns responses
├── service/       ← Business logic: the actual rules and operations
├── repository/    ← Data access: talks to PostgreSQL via JPA
├── entity/        ← Database models: maps to tables
├── dto/           ← Data Transfer Objects: what the API sends/receives
└── security/      ← (user module only) JWT handling
```

### How a request flows through these layers

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ CONTROLLER (AuthController.java)                         │
│                                                          │
│  @PostMapping("/api/v1/auth/register")                   │
│  public ResponseEntity<AuthResponse> register(request) { │
│      return authService.register(request);    ───────┐   │
│  }                                                   │   │
└──────────────────────────────────────────────────────┼───┘
                                                       │
    ▼                                                  │
┌──────────────────────────────────────────────────────┼───┐
│ SERVICE (AuthService.java)                           │   │
│                                                      ▼   │
│  public AuthResponse register(RegisterRequest req) {     │
│      // 1. Check if user exists                          │
│      if (userRepository.existsByEmail(req.email()))  ──┐ │
│          throw DuplicateResourceException;             │ │
│                                                        │ │
│      // 2. Hash password (never store plaintext!)      │ │
│      user.passwordHash = encoder.encode(req.password)  │ │
│                                                        │ │
│      // 3. Save to database                            │ │
│      userRepository.save(user);  ──────────────────────┤ │
│                                                        │ │
│      // 4. Generate JWT tokens                         │ │
│      return generateTokens(user);                      │ │
│  }                                                     │ │
└────────────────────────────────────────────────────────┼─┘
                                                         │
    ▼                                                    │
┌────────────────────────────────────────────────────────┼─┐
│ REPOSITORY (UserRepository.java)                       │ │
│                                                        ▼ │
│  interface UserRepository extends JpaRepository<User,Long>│
│      Optional<User> findByEmail(String email);           │
│      boolean existsByEmail(String email);                 │
│  }                                                       │
│                                                          │
│  Spring Data JPA auto-generates the SQL:                 │
│  SELECT * FROM users WHERE email = ?                     │
└──────────────────────────────────────────────────────────┘
                    │
                    ▼
            ┌──────────────┐
            │  PostgreSQL   │
            │  users table  │
            └──────────────┘
```

### Why separate layers?

1. **Controller** knows HTTP (request/response, status codes), but nothing about SQL
2. **Service** knows business rules ("is this email taken?"), but nothing about HTTP
3. **Repository** knows SQL/database, but nothing about business rules

If we switch from REST to GraphQL, only the controller changes. If we switch from PostgreSQL to MongoDB, only the repository changes. Each layer has **one reason to change**.

---

## 6. Database Design & Flyway Migrations

### What is Flyway?

Flyway is a **database migration tool**. Instead of manually running SQL scripts, you create versioned files:

```
db/migration/
├── V1__create_users.sql           ← Runs first
├── V2__create_stations_trains.sql ← Runs second
├── V3__create_routes_schedules.sql← Runs third
└── V4__create_coaches_seats.sql   ← Runs fourth
```

When the app starts, Flyway:
1. Checks which migrations have already run (stored in a `flyway_schema_history` table)
2. Runs only the new ones, in order
3. Never re-runs or modifies already-applied migrations

**Why Flyway matters at scale**: In a team of 10 developers, everyone's database stays in sync. In production, schema changes are applied automatically and safely during deployment. You can't "forget" to run a migration.

### The Database Schema

Here's what our tables look like and why each exists:

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   users     │     │   stations   │     │    trains     │
├─────────────┤     ├──────────────┤     ├──────────────┤
│ id          │     │ id           │     │ id           │
│ email  [UQ] │     │ code [UQ]    │◄────│ source_stn_id│
│ password_hash│    │ name         │◄────│ dest_stn_id  │
│ full_name   │     │ city         │     │ train_number │
│ role        │     │ state        │     │ name         │
│ created_at  │     │ zone         │     │ train_type   │
└─────────────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │
                    ┌──────┴───────┐     ┌──────┴───────┐
                    │route_stations│     │   routes     │
                    ├──────────────┤     ├──────────────┤
                    │ station_id   │◄────│ train_id     │
                    │ route_id     │────►│ route_name   │
                    │ sequence_num │     └──────┬───────┘
                    │ arrival_time │            │
                    │ departure_tm │     ┌──────┴───────┐
                    │ day_offset   │     │  schedules   │
                    └──────────────┘     ├──────────────┤
                                        │ train_id     │
        ┌──────────────┐                │ route_id     │
        │   coaches    │                │ runs_on_mon  │
        ├──────────────┤                │ runs_on_tue  │
        │ train_id     │                │ ...          │
        │ coach_number │                │ effective_frm│
        │ coach_type   │                └──────┬───────┘
        │ total_seats  │                       │
        └──────────────┘                ┌──────┴───────┐
                                        │  train_runs  │
                                        ├──────────────┤
        ┌──────────────┐                │ schedule_id  │
        │seat_inventory│                │ train_id     │
        ├──────────────┤                │ route_id     │
        │ train_run_id │───────────────►│ run_date     │
        │ coach_type   │                │ status       │
        │ from_stn_id  │                └──────────────┘
        │ to_stn_id    │
        │ total_seats  │
        │ avail_seats  │
        │ version [OL] │  ← Optimistic Locking
        └──────────────┘
```

### Key Design Decisions

#### 1. Segment-based seat inventory (the hardest part)

**The problem**: Unlike an airplane where a seat is either occupied or empty for the entire flight, a train seat is **reusable across segments**.

Example: Train runs Delhi → Jaipur → Ahmedabad → Mumbai
- Passenger A books Delhi → Jaipur (gets seat 15)
- At Jaipur, seat 15 is **freed**
- Passenger B can book Jaipur → Mumbai (gets the same seat 15)

So when checking if seat 15 is available for "Jaipur → Ahmedabad", we need to check if anyone is occupying it on the overlapping segment. The `seat_inventory` table tracks availability per `(train_run, coach_type, from_station, to_station)` combination.

#### 2. `train_runs` - materialized schedules

A schedule says "Train 12301 runs on Mon, Wed, Fri from Jan 1 to Dec 31". But when someone books a ticket for "March 15", we need a concrete record for that specific date.

`train_runs` is generated by a batch job: it reads schedules and creates one row per train per date, like:
```
| schedule_id | train_id | run_date   | status    |
|-------------|----------|------------|-----------|
| 1           | 101      | 2026-03-15 | SCHEDULED |
| 1           | 101      | 2026-03-17 | SCHEDULED |
```

**Why not compute it on the fly?** Because bookings, seat inventory, and cancellations all reference a specific `train_run_id`. Having a concrete row simplifies every query.

#### 3. `version` column (Optimistic Locking)

The `seat_inventory` table has a `version` column. When two users try to book the last seat simultaneously:

```
User A reads: available_seats = 1, version = 5
User B reads: available_seats = 1, version = 5

User A writes: UPDATE seat_inventory SET available_seats = 0, version = 6 WHERE id = ? AND version = 5
  → SUCCESS (version matched)

User B writes: UPDATE seat_inventory SET available_seats = 0, version = 6 WHERE id = ? AND version = 5
  → FAILS (version is now 6, not 5)
  → User B gets an error and must retry
```

This prevents **double booking** at the database level. JPA implements this with `@Version`.

#### 4. Index design

```sql
CREATE INDEX idx_seat_inv_lookup ON seat_inventory(train_run_id, coach_type, from_station_id, to_station_id);
```

This **composite index** covers the exact query pattern we use most: "find availability for this specific train run, coach type, and station pair." Without this index, PostgreSQL would scan the entire table. With it, the lookup is O(log n).

---

## 7. Authentication System (JWT)

### What is JWT?

**JSON Web Token** is a signed string that proves "this user is who they claim to be" without requiring the server to store session data.

```
┌─────────────────── JWT Token ──────────────────────┐
│                                                     │
│  Header.Payload.Signature                           │
│                                                     │
│  Header:  { "alg": "HS256", "typ": "JWT" }        │
│  Payload: { "sub": "user@email.com",               │
│             "userId": 42,                           │
│             "role": "PASSENGER",                    │
│             "exp": 1735689600 }                     │
│  Signature: HMAC-SHA256(header + payload, SECRET)   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### How our JWT system works

**File: `JwtProvider.java`** — Creates and validates tokens.

```
REGISTRATION/LOGIN FLOW:
========================

1. User sends: POST /api/v1/auth/register { email, password, fullName }

2. AuthService:
   a. Check email not already taken
   b. Hash password with BCrypt (NEVER store plaintext)
   c. Save user to PostgreSQL
   d. Generate two tokens:
      - Access Token  (expires in 1 hour)  → used for API calls
      - Refresh Token (expires in 24 hours) → used to get new access token

3. Return: { accessToken: "eyJ...", refreshToken: "eyJ...", expiresIn: 3600000 }


SUBSEQUENT API CALLS:
=====================

4. User sends: GET /api/v1/users/me
   Headers: Authorization: Bearer eyJ...

5. JwtAuthenticationFilter (runs before every request):
   a. Extract token from "Authorization: Bearer <token>" header
   b. Validate signature (was it signed by our secret key?)
   c. Check expiration (is it still valid?)
   d. Load user from database by email
   e. Set SecurityContext → Spring Security now knows who this user is

6. Controller can access the user via @AuthenticationPrincipal


TOKEN REFRESH FLOW:
===================

7. When access token expires (after 1 hour):
   User sends: POST /api/v1/auth/refresh { refreshToken: "eyJ..." }

8. AuthService validates the refresh token and issues new tokens.
   The user never has to re-enter their password.
```

### Why two tokens?

- **Access token** (1 hour): Short-lived for security. If stolen, the damage window is small.
- **Refresh token** (24 hours): Used only to get new access tokens. Stored more securely by the client.

### Why BCrypt for passwords?

```java
passwordEncoder.encode("mypassword")  →  "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
```

BCrypt is:
- **One-way**: You can't reverse it to get the original password
- **Salted**: Same password produces different hashes each time (prevents rainbow table attacks)
- **Slow by design**: Takes ~100ms to hash (prevents brute-force attacks)

---

## 8. Spring Security Configuration

**File: `SecurityConfig.java`**

This file defines **who can access what**. It's the bouncer at the door.

```java
http
    .csrf(disable)                                    // ① Disable CSRF
    .sessionManagement(STATELESS)                     // ② No server-side sessions
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()        // ③ Anyone can register/login
        .requestMatchers("/swagger-ui/**").permitAll()         // ④ Docs are public
        .requestMatchers(GET, "/api/v1/stations/**").permitAll() // ⑤ Anyone can search stations
        .requestMatchers(GET, "/api/v1/trains/**").permitAll()   // ⑥ Anyone can view trains
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")    // ⑦ Admin-only endpoints
        .anyRequest().authenticated()                            // ⑧ Everything else needs login
    )
    .addFilterBefore(jwtFilter, UsernamePasswordFilter.class) // ⑨ JWT check runs first
```

### Why these choices?

1. **CSRF disabled**: CSRF protection is for browser-based session cookies. We use JWT tokens in headers, so CSRF doesn't apply.

2. **Stateless sessions**: The server stores NOTHING about logged-in users. All auth info is in the JWT token itself. This is critical for scaling — any server instance can handle any request.

3. **Public endpoints**: You shouldn't need to login just to search for trains. That would kill user experience.

4. **Role-based access**: Admin endpoints (creating trains/stations) require `ROLE_ADMIN`. Regular users can only view and book.

5. **Filter chain**: The `JwtAuthenticationFilter` runs before Spring's default auth filter. It intercepts every request, checks for a JWT token, and sets up the security context.

---

## 9. Global Exception Handling

**File: `GlobalExceptionHandler.java`**

### The problem it solves

Without this, exceptions result in ugly Spring default error pages with stack traces. That's bad for:
- **Security**: Stack traces reveal internal implementation details
- **API consistency**: Every error should have the same JSON structure
- **User experience**: Error messages should be helpful, not technical

### How it works

```java
@RestControllerAdvice                    // ← Intercepts exceptions from ALL controllers
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)  // ← When a train/user/station isn't found
    public ResponseEntity<ErrorResponse> handleNotFound(...) {
        return 404 { code: "RESOURCE_NOT_FOUND", message: "Train not found with identifier: 12345" }
    }

    @ExceptionHandler(DuplicateResourceException.class) // ← When email/train number already exists
    public ResponseEntity<ErrorResponse> handleDuplicate(...) {
        return 409 { code: "DUPLICATE_RESOURCE", message: "User already exists with identifier: a@b.com" }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) // ← When @Valid fails
    public ResponseEntity<ErrorResponse> handleValidation(...) {
        return 400 { code: "VALIDATION_ERROR", message: "email: must not be blank, password: size must be..." }
    }
}
```

### The exception hierarchy

```
BusinessException (400 Bad Request)
├── ResourceNotFoundException (404 Not Found)
└── DuplicateResourceException (409 Conflict)
```

Every exception has an `errorCode` (machine-readable) and `message` (human-readable). The client can switch on `errorCode` to handle specific cases programmatically.

### Standard error response format

Every error from this API looks like:
```json
{
    "code": "RESOURCE_NOT_FOUND",
    "message": "Train not found with identifier: 99999",
    "timestamp": "2026-04-20T22:30:00Z"
}
```

---

## 10. DTO Pattern - Why Not Expose Entities

### The problem

Our `User` entity has a `passwordHash` field. If we returned the entity directly from the API:

```json
{
    "id": 1,
    "email": "user@example.com",
    "passwordHash": "$2a$10$N9qo8uLO...",   // LEAKED!
    "role": "PASSENGER"
}
```

### The solution: DTOs (Data Transfer Objects)

```
Entity (User.java)              DTO (UserResponse.java)
┌─────────────────┐            ┌─────────────────┐
│ id              │            │ id              │
│ email           │ ────────►  │ email           │
│ passwordHash    │   (map)    │ fullName        │
│ fullName        │            │ phone           │
│ phone           │            │ role            │
│ role            │            │ createdAt       │
│ emailVerified   │            └─────────────────┘
│ createdAt       │            (no passwordHash!)
│ updatedAt       │
└─────────────────┘
```

**DTOs serve three purposes:**

1. **Security**: Hide sensitive fields (password, internal IDs)
2. **Decoupling**: API shape can differ from database shape. Adding a column doesn't change the API.
3. **Validation**: Input DTOs have `@NotBlank`, `@Email` annotations. Entities don't validate — they just persist.

We use Java **records** for DTOs — they're immutable, concise, and auto-generate `equals`/`hashCode`/`toString`:

```java
public record UserResponse(
    Long id,
    String email,
    String fullName,
    Role role,
    Instant createdAt
) { }
```

---

## 11. Event Envelope - Preparing for Kafka

**File: `EventEnvelope.java`**

Even though Kafka isn't connected yet (Phase 3), we've already designed the event wrapper. This is the standard format for ALL events in the system.

```java
public class EventEnvelope<T> {
    String eventId;          // Unique ID for this specific event (UUID)
    String eventType;        // "BOOKING_CONFIRMED", "PAYMENT_SUCCESS", etc.
    String aggregateId;      // The entity this event is about (e.g., booking PNR)
    String aggregateType;    // "BOOKING", "PAYMENT", etc.
    Instant timestamp;       // When the event occurred
    int version;             // Schema version (for backward compatibility)
    String source;           // Which module published this ("railway-booking")
    String correlationId;    // Trace a request across multiple events
    String idempotencyKey;   // Prevent processing the same event twice
    T payload;               // The actual event data (generic type)
}
```

### Why each field matters

| Field | Purpose | Example |
|-------|---------|---------|
| `eventId` | Deduplicate: "have I seen this exact event before?" | `"a1b2c3d4-..."` |
| `eventType` | Route to the right handler | `"BOOKING_CONFIRMED"` |
| `aggregateId` | Which entity changed | PNR `"PNR1234567"` |
| `correlationId` | One user action generates multiple events. This links them. | Same ID across booking → payment → notification events |
| `idempotencyKey` | Network failures cause retries. This prevents double-processing. | Client-generated UUID |
| `version` | Event schema evolves. Consumers check version to parse correctly. | `1` |

### Why design this now?

When we add Kafka in Phase 3, every producer and consumer will use this envelope. Designing it early means we won't have to refactor the booking and payment modules later.

---

## 12. Configuration Management

**File: `application.yml`**

### Key configuration decisions

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate      # ① Flyway manages schema, Hibernate only validates
    open-in-view: false        # ② No lazy-loading in controllers (prevents N+1)
    properties:
      hibernate:
        jdbc.batch_size: 25    # ③ Batch inserts for performance
        order_inserts: true    # ④ Required for batching to work
```

**① `ddl-auto: validate`** — Hibernate checks that entities match the database schema but NEVER modifies the database. Flyway handles all schema changes. In production, you never want Hibernate auto-creating tables.

**② `open-in-view: false`** — This is a common Spring Boot gotcha. When `true` (the default), the database connection stays open throughout the entire HTTP request, including while rendering the response. This:
- Wastes database connections
- Hides N+1 query problems (lazy loading silently fires queries in the controller)
- Can cause `LazyInitializationException` in production under load

Setting it to `false` forces us to load all needed data in the service layer.

**③④ Batch inserts** — When saving 6 passengers in one booking, instead of:
```sql
INSERT INTO booking_passengers VALUES (...);
INSERT INTO booking_passengers VALUES (...);
INSERT INTO booking_passengers VALUES (...);
-- 6 separate round trips to the database
```
Batching sends:
```sql
INSERT INTO booking_passengers VALUES (...), (...), (...), (...), (...), (...);
-- 1 round trip
```

### HikariCP connection pool

```yaml
hikari:
  maximum-pool-size: 20    # Max 20 concurrent database connections
  minimum-idle: 5          # Keep at least 5 connections warm
```

Database connections are expensive to create (~50-100ms). A connection pool maintains pre-created connections. When your code needs a connection, it borrows one from the pool and returns it when done.

`maximum-pool-size: 20` means at most 20 requests can talk to the database simultaneously. The 21st request waits. This protects the database from being overwhelmed.

---

## 13. What Each Module Does

### railway-common

**Purpose**: Shared code that all modules need.

| File | What it does |
|------|-------------|
| `BaseEntity.java` | Every entity gets `createdAt` and `updatedAt` fields automatically (JPA auditing) |
| `EventEnvelope.java` | Standard wrapper for Kafka events (used in Phase 3+) |
| `ErrorResponse.java` | Standard JSON error format for all API errors |
| `PagedResponse.java` | Standard pagination wrapper: `{ content: [...], page: 0, totalPages: 5 }` |
| `BusinessException.java` | Base exception with error code |
| `ResourceNotFoundException.java` | 404 errors |
| `DuplicateResourceException.java` | 409 conflict errors |
| `GlobalExceptionHandler.java` | Catches all exceptions and returns consistent error JSON |
| `TrainRunEvent.java` | Kafka event payload for train run creation (triggers ES indexing) |
| `SearchDataProvider.java` | Interface for cross-module data access (Dependency Inversion) — implemented by railway-booking, consumed by railway-train |

### railway-user

**Purpose**: User registration, authentication (JWT), and profile management.

| File | What it does |
|------|-------------|
| `User.java` | JPA entity → `users` table |
| `Role.java` | Enum: `PASSENGER`, `ADMIN` |
| `UserRepository.java` | Database queries: `findByEmail`, `existsByEmail` |
| `JwtProvider.java` | Creates and validates JWT tokens using HMAC-SHA256 |
| `JwtAuthenticationFilter.java` | Intercepts every HTTP request, extracts JWT, sets security context |
| `AuthService.java` | Business logic: register, login, refresh tokens |
| `UserService.java` | Profile lookup by ID or email |
| `AuthController.java` | REST endpoints: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh` |
| `UserController.java` | REST endpoints: `GET /users/me`, `GET /users/{id}` (admin) |

### railway-train

**Purpose**: Train, station, route, schedule, and coach management (admin CRUD + public read).

| File | What it does |
|------|-------------|
| `Station.java` | JPA entity → `stations` table. Fields: code (NDLS), name, city, state, zone, lat/lng |
| `Train.java` | JPA entity → `trains` table. References source/dest stations. Has coaches collection. |
| `Route.java` | JPA entity → `routes` table. A sequence of stops for a train. |
| `RouteStation.java` | JPA entity → `route_stations` table. One stop: station + sequence + arrival/departure time + day offset |
| `Schedule.java` | JPA entity → `schedules` table. Which days a train runs (Mon/Tue/...) and effective date range |
| `Coach.java` | JPA entity → `coaches` table. Coach number (A1, B2), type (FIRST_AC, SLEEPER), seat count |
| `CoachType.java` | Enum: `FIRST_AC`, `SECOND_AC`, `THIRD_AC`, `SLEEPER`, `GENERAL` |
| `TrainType.java` | Enum: `RAJDHANI`, `SHATABDI`, `SUPERFAST`, `EXPRESS`, `MAIL`, `LOCAL` |
| `StationService.java` | Create station, search stations, find by code |
| `TrainService.java` | Create train (with coaches), find by train number |
| `RouteService.java` | Create route with station stops |
| `ScheduleService.java` | Create schedule (which days a train runs) |
| `StationController.java` | `GET /stations?q=del` (search), `POST /admin/stations` (create) |
| `TrainController.java` | `GET /trains` (list), `GET /trains/{number}`, `POST /admin/trains` |
| `RouteController.java` | `GET /routes/{id}`, `POST /admin/routes`, `POST /admin/schedules` |
| **Search (Phase 4)** | |
| `search/document/JourneyOptionDocument.java` | ES document: one per (trainRunId, fromStation, toStation) journey option |
| `search/document/CoachAvailability.java` | Nested ES type: seats per coach type |
| `search/document/FareInfo.java` | Nested ES type: fare per coach type |
| `search/repository/JourneyOptionSearchRepository.java` | Spring Data Elasticsearch repository for `journey_options` index |
| `search/service/JourneyDocumentBuilder.java` | Denormalization engine: builds ES documents from PostgreSQL data |
| `search/service/TrainSearchService.java` | Queries ES for train search: station matching, coach type filtering, sorting |
| `search/kafka/SearchIndexingConsumer.java` | Consumes train.events + booking.events → indexes/updates ES documents |
| `controller/TrainSearchController.java` | `GET /api/v1/trains/search?from=&to=&date=&coachType=` (public) |
| `controller/AdminSearchController.java` | `POST /api/v1/admin/search/reindex` (admin only, bulk reindex) |
| `dto/JourneySearchResponse.java` | Search result DTO with station info, availability, fares |
| `dto/StationSuggestionResponse.java` | Autocomplete suggestion DTO |
| `resources/elasticsearch/journey-options-settings.json` | ES index settings: edge_ngram analyzer for autocomplete |
| `resources/elasticsearch/journey-options-mappings.json` | ES field mappings: keyword, text, nested, scaled_float |

### railway-booking

**Purpose**: The heart of the system. Handles seat availability checking, distributed seat locking, booking creation, PNR status lookup, train run materialization, rate limiting, and idempotency.

| File | What it does |
|------|-------------|
| **Entities** | |
| `Booking.java` | JPA entity → `bookings` table. PNR, user, train run, status, fare, optimistic locking (`@Version`) |
| `BookingPassenger.java` | JPA entity → `booking_passengers` table. Per-passenger: name, age, seat/coach assignment, status |
| `SeatInventory.java` | JPA entity → `seat_inventory` table. Tracks available seats per (train_run, coach_type, from_station, to_station) segment |
| `TrainRun.java` | JPA entity → `train_runs` table. A concrete "Train X on Date Y" that can be booked |
| `BookingStatus.java` | Enum: INITIATED, PAYMENT_PENDING, CONFIRMED, WAITLISTED, RAC, CANCELLED, FAILED |
| **Redis Services** | |
| `SeatLockManager.java` | Distributed seat locking via Redis Lua scripts. Atomic check-and-decrement with TTL |
| `AvailabilityCache.java` | Write-through cache for seat availability. Key: `avail:{trainRunId}:{coachType}` |
| `PnrCache.java` | Cache-aside pattern for PNR lookups. Key: `pnr:{pnr}` |
| `IdempotencyStore.java` | Prevents duplicate bookings. 24hr TTL with processing/completed states |
| **Core Services** | |
| `BookingService.java` | The orchestrator: idempotency → validate → lock seats → create booking → evict cache |
| `SeatAvailabilityService.java` | Redis-first availability lookup with DB fallback |
| `PnrStatusService.java` | Redis-first PNR lookup with DB fallback |
| `TrainRunService.java` | Materializes schedules into bookable train runs with seat inventory |
| `PnrGenerator.java` | Generates unique PNR numbers: "PNR" + 7 random digits |
| **Rate Limiting** | |
| `RateLimit.java` | Custom annotation: `@RateLimit(requests = 5, windowSeconds = 60)` |
| `RateLimitInterceptor.java` | Redis sliding window via Lua script. Sets `X-RateLimit-Remaining` header |
| `BookingWebConfig.java` | Registers the rate limit interceptor on `/api/v1/**` |
| **Controllers** | |
| `BookingController.java` | `POST /bookings` (5/min limit), `GET /bookings/{pnr}`, `GET /bookings/my` |
| `AvailabilityController.java` | `GET /availability?trainRunId&coachType` (30/min limit) |
| `PnrController.java` | `GET /pnr/{pnr}` (20/min limit) |
| `AdminBookingController.java` | `POST /admin/train-runs/generate` (admin only) |
| **Kafka Publishers** | |
| `kafka/BookingEventPublisher.java` | Publishes booking lifecycle events to `booking.events` |
| `kafka/PaymentEventConsumer.java` | Consumes payment events → confirms/fails bookings |
| `kafka/TrainRunEventPublisher.java` | Publishes TRAIN_RUN_CREATED to `train.events` (triggers ES indexing) |
| **Cross-Module Search Support** | |
| `search/SearchDataProviderImpl.java` | Implements `SearchDataProvider` — provides train run info + seat inventory to railway-train's ES indexer |

### railway-payment

**Purpose**: Payment processing with mock gateway and Kafka event publishing.

| File | What it does |
|------|-------------|
| `entity/Payment.java` | JPA entity → `payments` table. Tracks booking, amount, status, gateway transaction |
| `entity/PaymentStatus.java` | Enum: INITIATED, PROCESSING, SUCCESS, FAILED, REFUNDED |
| `repository/PaymentRepository.java` | findByBookingId, findByPnr, findByIdempotencyKey |
| `gateway/MockPaymentGateway.java` | Simulates payment gateway: 90% success, 50-200ms delay, random failure reasons |
| `kafka/PaymentEventPublisher.java` | Publishes PAYMENT_SUCCESS/FAILED to `payment.events` topic |
| `service/PaymentService.java` | Orchestrator: idempotency → lookup booking → create payment → call gateway → publish event |
| `controller/PaymentController.java` | `POST /payments/initiate`, `GET /payments/booking/{id}`, `POST /payments/{id}/retry` |
| `dto/PaymentRequest.java` | bookingId + paymentMethod + optional idempotencyKey |
| `dto/PaymentResponse.java` | Full payment details including gateway transaction ID |

### railway-notification

**Purpose**: Consumes booking lifecycle events via Kafka and logs mock notifications (email/SMS implementation in Phase 5).

| File | What it does |
|------|-------------|
| `kafka/NotificationConsumer.java` | Listens on `booking.events` topic. Logs mock notifications for BOOKING_INITIATED, CONFIRMED, FAILED |

### railway-app

**Purpose**: The assembly module. Contains NO business logic — only configuration and infrastructure.

| File | What it does |
|------|-------------|
| `RailwayApplication.java` | Spring Boot main class. `@SpringBootApplication(scanBasePackages = "com.railway")` tells Spring to scan ALL modules |
| `SecurityConfig.java` | URL access rules, JWT filter chain, password encoder, auth manager |
| `SwaggerConfig.java` | OpenAPI documentation with JWT auth support |
| `RedisConfig.java` | RedisTemplate with Jackson JSON serializer for object storage |
| `KafkaConfig.java` | Topic creation (7 topics: booking, payment, notification, train events + retries + DLTs) |
| `application.yml` | All configuration: database, JWT, Redis, Kafka, Elasticsearch, rate limits, cache TTLs |
| `V1-V5 migrations` | SQL files that create all database tables (V5 adds booking tables) |

---

## 14. The Railway Domain - Key Concepts

### Real-world railway system concepts mapped to our code

| Real World | Our Code | Example |
|-----------|----------|---------|
| A railway station | `Station` entity, code = "NDLS" | New Delhi (NDLS), Mumbai Central (MMCT) |
| A train | `Train` entity, number = "12301" | Rajdhani Express 12301 |
| Train's route (list of stops) | `Route` + `RouteStation` | Delhi → Jaipur → Ahmedabad → Mumbai |
| Each stop's timing | `RouteStation.arrivalTime/departureTime` | Arrives Jaipur 05:30, Departs 05:35 |
| Overnight trains | `RouteStation.dayOffset` | 0 = same day, 1 = next day |
| Train runs on Mon/Wed/Fri | `Schedule.runsOnMonday = true`, etc. | |
| "Train 12301 on March 15" | `TrainRun` (schedule + date) | A concrete instance you can book |
| Coach A1 has 72 seats | `Coach` entity | coach_number="A1", coach_type=FIRST_AC, total_seats=72 |
| How many seats available? | `SeatInventory` | Per train_run, per coach_type, per segment |
| Ticket classes | `CoachType` enum | FIRST_AC, SECOND_AC, THIRD_AC, SLEEPER, GENERAL |

### What makes railway booking different from flight booking?

1. **Segment-based seats**: Explained in Section 6. A seat on Delhi→Mumbai isn't simply "taken" — it might be available for Jaipur→Mumbai if the Delhi→Jaipur passenger gets off.

2. **Waitlist/RAC**: If confirmed seats are full, you can still get a "Waitlisted" or "RAC" (Reservation Against Cancellation) ticket. When someone cancels, waitlisted passengers get promoted automatically.

3. **Multiple passengers per booking**: One PNR can have up to 6 passengers, each with different berth preferences and statuses.

4. **Coach composition**: Different trains have different coach compositions. A Rajdhani might have 4 First AC + 8 Second AC coaches. A local train might have only General coaches.

---

## 15. Redis & Distributed Systems Patterns (Phase 2)

Phase 2 introduces Redis as a critical infrastructure component alongside PostgreSQL. Redis is an in-memory data store — think of it as a super-fast HashMap that lives outside your application, shared across all instances.

### Why Redis? Why not just use PostgreSQL for everything?

| Concern | PostgreSQL | Redis |
|---------|-----------|-------|
| **Speed** | ~1-5ms per query (disk-based) | ~0.1ms per operation (in-memory) |
| **Concurrency** | Row locks, potential deadlocks | Single-threaded, atomic operations |
| **TTL (auto-expiry)** | Requires scheduled jobs | Built-in per-key TTL |
| **Distributed locking** | Advisory locks (tricky to use) | Purpose-built primitives |

We use **both** together. PostgreSQL is the **source of truth** (durable, ACID). Redis is the **speed layer** (caching, locking, rate limiting). If Redis goes down, the system still works — just slower.

### Pattern 1: Distributed Seat Locking (SeatLockManager)

**The problem**: User A and User B both try to book the last seat on the same train at the same instant. Without coordination, both bookings succeed and we've sold a phantom seat.

**Why not just use database locks?** Database row locks work for a single database. But they hold connections open during the entire payment window (up to 10 minutes). With 1000 concurrent bookings, you'd need 1000 database connections — killing your connection pool.

**The solution**: Redis distributed lock with Lua script.

```
Lua script runs ATOMICALLY on Redis (single-threaded, no interruption):

1. Read current available seats count
2. If seats >= requested amount:
   a. Decrement the availability counter
   b. Set a lock key with the booking ID as owner
   c. Set TTL (10 minutes) — auto-releases if payment never completes
   d. Return SUCCESS
3. Else: Return FAILURE
```

**Why Lua scripts?** Redis is single-threaded, but individual commands could interleave between your application's check-and-set. A Lua script runs as one atomic unit — no other command can execute in the middle.

**Key design decisions:**
- **10-minute TTL**: If the user never pays, the lock auto-releases and seats become available again
- **Lock owner tracking**: We store the booking ID in the lock value so only the booking that locked the seats can release them
- **Dual-layer safety**: Redis lock (fast, might lose data on crash) + PostgreSQL `@Version` optimistic lock (durable, catches any slip-through)

```
Redis Keys:
  seat-lock:{trainRunId}:{coachType}:{fromStation}:{toStation}:{bookingId}  →  "1"  (TTL: 600s)
  seat-avail:{trainRunId}:{coachType}:{fromStation}:{toStation}             →  "42" (available count)
```

### Pattern 2: Write-Through Cache (AvailabilityCache)

**The problem**: Seat availability is the most-read data in the system. Every search query checks it. Hitting PostgreSQL for every availability check would overwhelm the database.

**Write-through** means: every time data changes, we update BOTH the database AND the cache. The cache is always up-to-date.

```
READ path:
  Client asks "how many seats?" → Check Redis → Hit? Return cached data
                                              → Miss? Query PostgreSQL → Store in Redis → Return

WRITE path:
  Booking happens → Update PostgreSQL → Update Redis → Return
  Cancellation    → Update PostgreSQL → Evict Redis  → Return
```

**Why not cache-aside here?** Cache-aside only populates the cache on reads. With availability, stale data means selling seats that don't exist. Write-through guarantees the cache always reflects reality.

**TTL (5 minutes)**: Even with write-through, we set a TTL as a safety net. If a cache update fails somehow, the stale entry expires within 5 minutes and the next read re-populates from PostgreSQL.

```
Redis Key: avail:{trainRunId}:{coachType}  →  JSON{ totalSeats, availableSeats, racSeats, ... }
```

### Pattern 3: Cache-Aside (PnrCache)

**The problem**: PNR status lookups are frequent but PNR data changes infrequently (only on booking status changes). We want fast reads without always hitting the database.

**Cache-aside** (also called "lazy loading") means: only populate the cache when someone asks for the data.

```
READ path:
  Client asks "PNR status?" → Check Redis → Hit? Return cached data (fast!)
                                           → Miss? Query PostgreSQL → Store in Redis → Return

WRITE path (on booking status change):
  Status changes → Update PostgreSQL → EVICT from Redis (don't update)
  Next read will re-populate the cache from the fresh DB data
```

**Why evict instead of update?** PNR status has complex nested data (multiple passengers, each with their own status). It's simpler and safer to just delete the cached entry and let the next read rebuild it, rather than trying to surgically update nested JSON.

**TTL (15 minutes)**: Longer than availability cache because PNR data changes less frequently. If someone checks their PNR and it's been evicted, the worst case is a single database query to re-cache.

```
Redis Key: pnr:{pnr}  →  JSON{ pnr, status, passengers: [...] }
```

### Pattern 4: Sliding Window Rate Limiting (RateLimitInterceptor)

**The problem**: Without rate limiting, a single user (or bot) can flood your API with thousands of requests per second, degrading service for everyone.

**Why not a simple counter?** A fixed counter like "100 requests per minute" has a boundary problem: a user could send 100 requests at 11:59:59 and 100 more at 12:00:01 — that's 200 requests in 2 seconds, all "within the limit."

**Sliding window** tracks the exact timestamp of each request:

```
Lua script (atomic):
1. Remove all entries older than the window (e.g., > 60 seconds ago)
2. Count remaining entries
3. If count < limit:
   a. Add current timestamp to the sorted set
   b. Set key TTL = window size
   c. Return remaining quota
4. Else: Return -1 (rate limited)
```

**Why Redis sorted sets?** A sorted set (ZSET) stores members sorted by score. We use timestamps as scores. `ZREMRANGEBYSCORE` efficiently removes expired entries. `ZCARD` counts remaining entries. It's the perfect data structure for sliding windows.

**Per-endpoint limits:**
- Availability search: 30 requests/minute (frequent, lightweight)
- Booking creation: 5 requests/minute (expensive, involves locking)
- PNR check: 20 requests/minute (moderate)

```
Redis Key: rate-limit:{prefix}:{userId}  →  Sorted Set { (timestamp1, member1), (timestamp2, member2), ... }
```

### Pattern 5: Idempotency (IdempotencyStore)

**The problem**: Networks are unreliable. A user clicks "Book" but gets a timeout. Did the booking succeed? They click again. Without idempotency, they might get charged twice for the same booking.

**Idempotency** means: sending the same request multiple times produces the same result as sending it once.

```
First request with key "abc-123":
  1. Check Redis: key "abc-123" → not found
  2. Set Redis: "abc-123" → "PROCESSING" (with 24hr TTL)
  3. Process booking normally
  4. On success: Update Redis: "abc-123" → "COMPLETED|PNR1234567"
  5. Return booking response

Second request with same key "abc-123":
  1. Check DB first: find booking with idempotency_key = "abc-123" → found!
  2. Return the SAME booking response (no duplicate booking created)

Second request while first is still processing:
  1. Check DB: not found yet
  2. Check Redis: "abc-123" → "PROCESSING"
  3. Return error: "Request is already being processed"
```

**Two layers of protection:**
- **Redis**: Fast check for in-flight requests (prevents double-submit within milliseconds)
- **PostgreSQL**: Durable check via unique constraint on `idempotency_key` column (catches anything Redis misses)

**24-hour TTL**: The idempotency key expires after 24 hours. After that, the same key can be reused. This balances protection against duplicates vs. not storing keys forever.

### Pattern 6: Optimistic Locking (@Version)

**The problem**: Two threads read the same `seat_inventory` row showing 5 available seats. Both try to decrement to 4. Without protection, we lose one decrement — showing 4 seats when there should be 3.

**Optimistic locking** means: don't lock the row upfront. Instead, check at write time that nobody else modified it.

```sql
-- Repository method:
UPDATE seat_inventory
SET available_seats = available_seats - :count, version = version + 1
WHERE id = :id AND version = :expectedVersion

-- If version changed between read and write, updated_rows = 0 → we know there was a conflict
```

**Why "optimistic"?** We optimistically assume no conflict will happen (which is true 99% of the time). We only handle conflicts when they occur. Compare with "pessimistic locking" (SELECT FOR UPDATE) which blocks other readers/writers preemptively.

**How it works with Redis locks**: Redis lock is the first line of defense (fast, distributed). `@Version` is the safety net in case the Redis lock fails (Redis crashed, TTL expired early, etc.). Belt and suspenders.

---

## 16. The Booking Engine — How It All Fits Together

### The Complete Booking Flow

Here's what happens when a user clicks "Book Now", step by step:

```
User clicks "Book Now"
       │
       ▼
┌──────────────────────┐
│  1. IDEMPOTENCY      │  Check if this exact request was already processed
│     CHECK             │  Redis (fast) → DB unique constraint (durable)
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  2. VALIDATE          │  Check: ≤6 passengers, different from/to stations,
│     REQUEST           │  train run is SCHEDULED status
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  3. CHECK             │  Redis cache (0.1ms) → PostgreSQL fallback (2ms)
│     AVAILABILITY      │  "Are there enough seats on this segment?"
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  4. LOCK SEATS        │  Redis Lua script: atomically decrement availability
│     IN REDIS          │  Set lock with 10-min TTL (payment window)
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  5. DECREMENT DB      │  PostgreSQL: UPDATE with @Version check
│     (OPTIMISTIC LOCK) │  If version conflict → release Redis lock → retry
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  6. CREATE BOOKING    │  PostgreSQL: INSERT booking + passengers
│     IN DATABASE       │  Status: PAYMENT_PENDING, generate PNR
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  7. EVICT CACHE       │  Remove stale availability from Redis
│                       │  Next read will re-populate from DB
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  8. RETURN PNR        │  HTTP 201: { pnr: "PNR1234567", status: "PAYMENT_PENDING" }
│     TO USER           │  User has 10 minutes to complete payment
└──────────────────────┘
```

### Train Run Materialization

Before anyone can book a ticket, we need to materialize abstract schedules into concrete, bookable train runs:

```
Schedule: "Train 12301 runs Mon/Wed/Fri"
    +
Date Range: "April 1 - April 30"
    ↓
Materialized TrainRuns:
    - Train 12301, April 1 (Monday)     ← each is a bookable entity
    - Train 12301, April 3 (Wednesday)
    - Train 12301, April 5 (Friday)
    - ... etc

For each TrainRun, create SeatInventory:
    - TrainRun #1, FIRST_AC, Delhi→Jaipur, 72 seats available
    - TrainRun #1, FIRST_AC, Jaipur→Ahmedabad, 72 seats available
    - TrainRun #1, SLEEPER, Delhi→Jaipur, 200 seats available
    - ... (every coach_type × every consecutive station pair)
```

**Why materialize?** You can't query "is Train 12301 available on April 5?" efficiently against a schedule table. Materializing creates concrete rows you can index, cache, and lock.

### Expired Booking Cleanup

A safety net for when users abandon bookings without paying:

```
@Scheduled(every 10 minutes):
  1. Find all bookings where status = PAYMENT_PENDING
     AND createdAt < (now - 10 minutes)
  2. For each expired booking:
     a. Set status → FAILED
     b. INCREMENT seat_inventory (give seats back)
     c. Evict availability cache (force refresh)
     d. Evict PNR cache
  3. Log how many bookings were cleaned up
```

This runs alongside Redis TTL auto-expiry. Redis handles the lock release; this job handles the database state cleanup.

### Segment-Based Availability Recap

Remember from Section 6: railway seats are reusable across segments. When checking availability for Delhi→Mumbai on a route Delhi→Jaipur→Ahmedabad→Mumbai:

```
A booking from Delhi→Ahmedabad must decrement:
  - Delhi→Jaipur segment     (passenger occupies seat here)
  - Jaipur→Ahmedabad segment (passenger still on the train)

But NOT:
  - Ahmedabad→Mumbai segment (passenger got off — seat is free!)
```

The `seat_inventory` table has one row per (train_run, coach_type, from_station, to_station) for consecutive station pairs. This is the fundamental data model that makes Indian railway booking work.

---

## 17. Kafka & Event-Driven Architecture (Phase 3)

Phase 3 introduces Apache Kafka for asynchronous, event-driven communication between modules. Instead of direct method calls, services publish events to Kafka topics. Other services consume those events independently.

### Why Events? Why not just call the payment service directly?

| Direct Calls | Event-Driven |
|-------------|-------------|
| Booking module must know about payment module | Booking just publishes "BOOKING_INITIATED" — doesn't care who listens |
| If payment is down, booking fails | Booking succeeds immediately; payment processes when ready |
| Adding notifications = modifying booking code | Add a new consumer — booking code stays unchanged |
| Tight coupling between modules | Loose coupling — modules communicate through shared events |

### Kafka Architecture in Our System

```
                    ┌──────────────────┐
                    │   booking.events │ (3 partitions)
                    │   Topic          │
                    └──────┬───────────┘
                           │
    ┌──────────────────────┼──────────────────────┐
    │                      │                      │
    ▼                      ▼                      ▼
┌─────────────┐   ┌──────────────┐   ┌──────────────────┐
│ Search      │   │ Notification │   │ (future)         │
│ Indexing    │   │ Consumer     │   │ Analytics        │
│ Consumer   │   │ (logs mock   │   │ Consumer         │
│ (updates   │   │  email/SMS)  │   │                  │
│  ES avail) │   └──────────────┘   └──────────────────┘
└─────────────┘

                    ┌──────────────────┐
                    │  payment.events  │ (3 partitions)
                    │  Topic           │
                    └──────┬───────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ Payment      │
                    │ Event        │
                    │ Consumer     │
                    │ (confirms/   │
                    │  fails       │
                    │  bookings)   │
                    └──────────────┘

                    ┌──────────────────┐
                    │  train.events    │ (3 partitions)   [Phase 4]
                    │  Topic           │
                    └──────┬───────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ Search       │
                    │ Indexing     │
                    │ Consumer     │
                    │ (builds ES  │
                    │  documents)  │
                    └──────────────┘
```

### Topics We Create

| Topic | Partitions | Key | Purpose |
|-------|-----------|-----|---------|
| `booking.events` | 3 | trainRunId | Booking lifecycle events |
| `payment.events` | 3 | bookingId | Payment result events |
| `train.events` | 3 | trainId | Train run creation → triggers ES indexing |
| `notification.events` | 3 | — | (Phase 5) notification delivery |
| `booking.events.retry` | 1 | — | Auto-retry on processing failure |
| `booking.events.dlt` | 1 | — | Dead letter — unprocessable messages |
| `payment.events.dlt` | 1 | — | Dead letter for payment events |

**Partition key matters!** We key `booking.events` by `trainRunId` so all events for the same train run land on the same partition — guaranteeing **ordering** within a train run. We key `payment.events` by `bookingId` for ordering within a booking.

### EventEnvelope — The Standard Message Wrapper

Every Kafka message is wrapped in `EventEnvelope<T>`:

```java
EventEnvelope<BookingEvent> envelope = EventEnvelope.<BookingEvent>builder()
    .eventType("BOOKING_INITIATED")     // What happened
    .aggregateId("42")                  // Which entity
    .aggregateType("Booking")           // Entity type
    .source("railway-booking")          // Which module published it
    .correlationId("PNR1234567")        // For request tracing
    .idempotencyKey("abc-123")          // Prevent duplicate processing
    .payload(bookingEvent)              // The actual event data
    .build();
// eventId (UUID) and timestamp auto-generated
```

**Why a wrapper?** Every consumer needs metadata: who published it, when, what type. Without a wrapper, every event type would need to duplicate these fields. The envelope standardizes it.

### Dead Letter Topics (DLT)

When a consumer fails to process a message after 3 retries:

```
Message arrives → Consumer throws exception
  → Retry 1 (after 1 second)
  → Retry 2 (after 2 seconds)
  → Retry 3 (after 4 seconds) — exponential backoff
  → All retries exhausted → Message sent to .dlt topic
  → @DltHandler logs the failure for manual investigation
```

This prevents a single bad message from blocking the entire topic. The DLT acts as a quarantine — you can inspect and replay messages later.

### The Payment Module

| Component | Purpose |
|-----------|---------|
| `Payment` entity | Maps to `payments` table. Tracks: bookingId, amount, status, gateway transaction ID |
| `PaymentStatus` enum | INITIATED → PROCESSING → SUCCESS / FAILED / REFUNDED |
| `MockPaymentGateway` | Simulates a real payment gateway. 90% success, 10% failure. 50-200ms delay. |
| `PaymentService` | Orchestrator: idempotency → create payment → call gateway → publish Kafka event |
| `PaymentEventPublisher` | Publishes `PAYMENT_SUCCESS` or `PAYMENT_FAILED` to `payment.events` |
| `PaymentController` | REST: initiate payment, check status, retry failed payment |

---

## 18. The Payment Flow — End to End

```
Step 1: User creates booking
    POST /api/v1/bookings
    → BookingService creates booking (PAYMENT_PENDING)
    → Publishes BOOKING_INITIATED to booking.events
    → Returns PNR to user

Step 2: User initiates payment
    POST /api/v1/payments/initiate  { bookingId, paymentMethod }
    → PaymentService looks up booking (pnr, amount, status)
    → Creates Payment record (PROCESSING)
    → Calls MockPaymentGateway
    → Gateway returns success/failure

Step 3a: Payment succeeds
    → PaymentService updates payment to SUCCESS
    → Publishes PAYMENT_SUCCESS to payment.events
    → PaymentEventConsumer (in booking module) receives event:
        • Sets booking status = CONFIRMED, sets bookedAt
        • Evicts availability + PNR caches
        • Publishes BOOKING_CONFIRMED to booking.events
    → NotificationConsumer logs mock confirmation email

Step 3b: Payment fails
    → PaymentService updates payment to FAILED
    → Publishes PAYMENT_FAILED to payment.events
    → PaymentEventConsumer receives event:
        • Sets booking status = FAILED
        • Restores seat inventory (+1 available seats)
        • Releases Redis seat lock
        • Evicts caches
        • Publishes BOOKING_FAILED to booking.events
    → NotificationConsumer logs mock failure notification
    → User can retry: POST /api/v1/payments/{paymentId}/retry
```

### Idempotency in the Payment Flow

**The problem:** What if `PAYMENT_SUCCESS` is delivered twice (Kafka's at-least-once semantics)?

**Our solution:** The `PaymentEventConsumer` checks the current booking status before processing:

```java
if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
    log.info("Already confirmed, skipping duplicate event");
    return;  // Safe to ignore
}
```

This makes the consumer **idempotent** — processing the same event multiple times has no additional effect.

---

## 19. CQRS & Elasticsearch Search Pipeline (Phase 4)

Phase 4 introduces the **CQRS pattern** (Command Query Responsibility Segregation) — the system now has separate write and read paths. Writes go to PostgreSQL (the source of truth). Reads for train search go to Elasticsearch (optimized for fast, full-text queries). Kafka events keep the two stores in sync.

### Why separate read and write stores?

| Concern | PostgreSQL (write path) | Elasticsearch (read path) |
|---------|------------------------|--------------------------|
| **Optimized for** | Transactional integrity, ACID | Full-text search, faceted queries |
| **Query speed** | Slow for multi-table JOINs across trains+routes+stations+inventory | Sub-millisecond — all data denormalized into one document |
| **Autocomplete** | `LIKE '%new del%'` — can't use index, full table scan | Edge ngram tokenizer — instant prefix matching |
| **Schema** | Normalized (3NF) — no data duplication | Denormalized — one document has everything needed for display |
| **Scaling** | Vertical (bigger server) | Horizontal (add more nodes, shard the index) |

The tradeoff is **eventual consistency** — after a booking changes seat availability in PostgreSQL, there's a ~1-2 second lag before the ES search index reflects the change. For train search, this is perfectly acceptable.

### The CQRS Architecture

```
┌─── WRITE PATH (PostgreSQL) ────────────────────────────────┐
│  Admin API → TrainRunService → PostgreSQL                   │
│  Booking API → BookingService → PostgreSQL                  │
│           ↓ Kafka events                                    │
├─── EVENT BUS (Kafka) ──────────────────────────────────────┤
│  train.events:   TRAIN_RUN_CREATED                         │
│  booking.events: BOOKING_CONFIRMED / BOOKING_FAILED        │
│           ↓                                                 │
├─── READ PATH (Elasticsearch) ──────────────────────────────┤
│  SearchIndexingConsumer → JourneyDocumentBuilder            │
│           → Elasticsearch (journey_options index)           │
│  TrainSearchService ← GET /api/v1/trains/search            │
└─────────────────────────────────────────────────────────────┘
```

**Key rule:** Writes never touch Elasticsearch directly. PostgreSQL is always the source of truth. Events are the sync mechanism. A bulk reindex endpoint (`POST /api/v1/admin/search/reindex`) is the safety net for when ES drifts out of sync.

### Document Model — JourneyOptionDocument

The core design decision: **one ES document per (trainRunId, fromStationId, toStationId) combination**. A train with 5 stops creates C(5,2) = 10 journey option documents.

```
Train: Delhi → Jaipur → Ahmedabad → Mumbai (3 consecutive segments)

Documents created:
  1. Delhi → Jaipur          (1 segment)
  2. Delhi → Ahmedabad       (2 segments)
  3. Delhi → Mumbai          (3 segments)
  4. Jaipur → Ahmedabad      (1 segment)
  5. Jaipur → Mumbai         (2 segments)
  6. Ahmedabad → Mumbai      (1 segment)
```

Each document is **fully denormalized** — it contains everything needed to display a search result:

```
JourneyOptionDocument {
    id:               "42_1_3"            // trainRunId_fromStationId_toStationId
    trainRunId:       42
    trainNumber:      "12301"
    trainName:        "Rajdhani Express"
    trainType:        "RAJDHANI"
    runDate:          "2026-04-25"
    fromStationCode:  "NDLS"
    fromStationName:  "New Delhi"
    toStationCode:    "ADI"
    toStationName:    "Ahmedabad Junction"
    departureTime:    "16:25"
    arrivalTime:      "07:40"
    durationMinutes:  915
    distanceKm:       935
    coachAvailabilities: [              // NESTED type in ES
        { coachType: "FIRST_AC",  availableSeats: 42, totalSeats: 72, ... },
        { coachType: "SLEEPER",   availableSeats: 180, totalSeats: 200, ... }
    ]
    fares: [
        { coachType: "FIRST_AC",  baseFare: 3450.00 },
        { coachType: "SLEEPER",   baseFare: 750.00 }
    ]
}
```

**Why denormalize?** In PostgreSQL, displaying a search result requires JOINing trains + routes + route_stations + seat_inventory + coaches. In ES, one document read returns everything. No JOINs = fast queries.

**Why nested type for coachAvailabilities?** Without nested mapping, ES flattens arrays. If you have `[{type: "FIRST_AC", available: 0}, {type: "SLEEPER", available: 50}]` and search for "FIRST_AC with available > 0", ES would cross-match FIRST_AC with SLEEPER's availability and return a false positive. Nested mapping prevents this.

### Elasticsearch Index Configuration

The `journey_options` index uses custom analyzers for autocomplete:

```
Edge Ngram Analyzer (for station names):
  Input:  "New Delhi"
  Tokens: ["ne", "new", "new ", "new d", "new de", "new del", "new delh", "new delhi"]

  When user types "new d" → matches "New Delhi" immediately
```

**Settings** (`journey-options-settings.json`):
- `autocomplete_analyzer`: edge_ngram tokenizer (min=2, max=20) + lowercase filter — used at index time
- `autocomplete_search_analyzer`: standard tokenizer + lowercase — used at search time (prevents the search query itself from being ngram-tokenized)

**Mappings** (`journey-options-mappings.json`):
- Station codes, train number → `keyword` (exact match only)
- Station names → `text` with `autocomplete_analyzer` (prefix search)
- Coach availabilities → `nested` (prevents cross-object matching)
- Base fare → `scaled_float` with scaling_factor 100 (stores cents as longs, efficient for range queries)
- Run date → `date` format

### The Indexing Pipeline

**1. Train run creation (Kafka-driven):**
```
Admin generates train runs → TrainRunService saves to PostgreSQL
  → TrainRunEventPublisher publishes TRAIN_RUN_CREATED to train.events
  → SearchIndexingConsumer receives event
  → JourneyDocumentBuilder.buildDocuments(trainRunId):
      a. Load train, route, route stations, seat inventory from PostgreSQL
      b. For every (fromStation, toStation) pair:
         - Calculate departure/arrival times (with dayOffset)
         - Calculate distance from distanceFromOriginKm deltas
         - Map seat inventory to CoachAvailability per coach type
         - Calculate fares based on coach type
      c. Index all documents into Elasticsearch
```

**2. Booking events (availability updates):**
```
Booking confirmed/failed → BookingEventPublisher publishes to booking.events
  → SearchIndexingConsumer receives BOOKING_CONFIRMED or BOOKING_FAILED
  → Re-queries seat_inventory from PostgreSQL
  → Updates coachAvailabilities in existing ES documents for that trainRunId
```

**3. Bulk reindex (safety net):**
```
Admin → POST /api/v1/admin/search/reindex
  → Delete all documents from journey_options index
  → Query all active (SCHEDULED) train run IDs from PostgreSQL
  → For each trainRunId: build all journey documents and bulk-index
  → Return: { documentsIndexed: 1250 }
```

### Cross-Module Data Access — Dependency Inversion

The `JourneyDocumentBuilder` (in `railway-train`) needs data from `railway-booking` (TrainRun, SeatInventory). But `railway-train` cannot depend on `railway-booking` — that would violate our module dependency rule.

**Solution:** Dependency Inversion Principle.

```
railway-common (defines the contract):
    SearchDataProvider interface
        getTrainRunInfo(Long trainRunId)
        getSegmentAvailabilities(Long trainRunId)
        getAllActiveTrainRunIds()

railway-booking (implements the contract):
    SearchDataProviderImpl
        @Service, delegates to TrainRunRepository + SeatInventoryRepository

railway-train (consumes the contract):
    JourneyDocumentBuilder
        @Autowired SearchDataProvider  ← gets the booking impl at runtime
```

Both modules depend on the abstraction (in `railway-common`), not on each other. Spring's component scanning wires the concrete `SearchDataProviderImpl` at runtime. This is the same pattern used by the Java `JDBC` spec — your code depends on `java.sql.Connection`, not on `com.mysql.jdbc.ConnectionImpl`.

### Search API

```
GET /api/v1/trains/search?from=NDLS&to=ADI&date=2026-04-25&coachType=SLEEPER

The query builds:
  bool:
    must:
      - term: runDate = "2026-04-25"
      - bool (from station):
          should:
            - term: fromStationCode = "NDLS"       (exact code match)
            - match: fromStationName = "NDLS"       (autocomplete match)
          minimum_should_match: 1
      - bool (to station):
          should:
            - term: toStationCode = "ADI"
            - match: toStationName = "ADI"
          minimum_should_match: 1
    must (optional, if coachType provided):
      - nested:
          path: coachAvailabilities
          query: term coachAvailabilities.coachType = "SLEEPER"
  sort: departureTime ASC
```

Users can search by station **code** (NDLS) or station **name** ("New Delhi") — the query matches either. The `coachType` filter is optional — without it, all coach types are returned.

---

## 20. Cancellation, Refund & Waitlist Promotion — Event Choreography (Phase 5)

Phase 5 adds the **most complex event flow** in the system: a single cancellation triggers a chain of events across three modules.

### The Choreography Chain

```
POST /api/v1/bookings/{pnr}/cancel
  └─> CancellationService (booking module)
        ├─ DB: booking → CANCELLED, restore inventory
        ├─ Redis: release lock, evict caches
        └─ Kafka: BOOKING_CANCELLED → booking.events
              ├─> BookingCancelledConsumer (payment module, group: payment-refund-service)
              │     ├─ PaymentService.initiateRefund()
              │     ├─ MockPaymentGateway.processRefund() — 95% success rate
              │     └─ Kafka: PAYMENT_REFUNDED → payment.events
              │           └─> NotificationConsumer → refund notification
              ├─> WaitlistPromotionConsumer (booking module, group: booking-waitlist-service)
              │     ├─ Find next RAC booking → promote to CONFIRMED
              │     ├─ Find next WAITLISTED → promote to RAC
              │     └─ Kafka: BOOKING_PROMOTED → booking.events
              │           └─> NotificationConsumer → promotion notification
              └─> NotificationConsumer → cancellation notification
```

### Why Event Choreography (Not Orchestration)

In **orchestration**, a central coordinator (saga) calls each step in sequence. In **choreography**, each service reacts to events independently — there is no central controller. We chose choreography because:

1. **Module boundaries stay clean** — `railway-booking` does NOT depend on `railway-payment` at compile time
2. **Each consumer is independently deployable, retryable, and has its own DLT**
3. **Adding new reactions is additive** — adding a "loyalty points" consumer requires zero changes to existing code

### Cancellation Service — The Trigger

```
CancellationService.cancelBooking(pnr, userId, isAdmin, reason)
├─ Authorization: only booking owner or ADMIN
├─ Validates: only CONFIRMED, RAC, WAITLISTED can be cancelled
├─ Captures previousStatus BEFORE transition (needed for promotion logic)
├─ Inventory restore:
│   ├─ CONFIRMED → incrementAvailableSeats (seat freed)
│   ├─ RAC → decrementRacSeats (RAC slot freed)
│   └─ WAITLISTED → decrementWaitlistCount (waitlist slot freed)
├─ Redis: release lock (best effort), evict caches
└─ Publishes BOOKING_CANCELLED with previousStatus in event payload
```

### Refund Flow

The `BookingCancelledConsumer` in `railway-payment` listens to `booking.events`:
- On `BOOKING_CANCELLED`: calls `PaymentService.initiateRefund(bookingId)`
- Finds the `SUCCESS` payment, calls `MockPaymentGateway.processRefund()`
- On success: sets payment status to `REFUNDED`, publishes `PAYMENT_REFUNDED`
- On failure: throws (triggers `@RetryableTopic` retry, 3 attempts → DLT)

### Waitlist Promotion — The Chain Reaction

When a CONFIRMED booking is cancelled, the freed seat cascades:

```
CONFIRMED cancelled → availableSeats++
  └─ Find oldest RAC booking → promote RAC → CONFIRMED (bookedAt set)
     └─ racSeats--
        └─ Find oldest WAITLISTED → promote WAITLISTED → RAC
           └─ waitlistCount--
```

**Idempotency**: Uses Redis `setIfAbsent("promotion:{eventId}", "1", 24h)` to prevent double-promotion if the same BOOKING_CANCELLED event is redelivered.

### RAC and Waitlist Booking

The booking initiation now branches based on seat availability:

```
if availableSeats >= passengerCount:
    → PAYMENT_PENDING (existing flow)
elif racSeats + passengerCount <= 10% of totalSeats:
    → RAC (passengers get racNumber)
else:
    → WAITLISTED (passengers get waitlistNumber)
```

RAC/WAITLISTED bookings still go through payment (consistent with Indian Railways: you pay even for waitlisted tickets). The `PaymentEventConsumer` preserves RAC/WAITLISTED status on payment success — only `PAYMENT_PENDING` becomes `CONFIRMED`.

### Notification Improvements

`NotificationConsumer` now:
- Has `@RetryableTopic` with 3 retries and DLT
- Handles `BOOKING_CANCELLED` and `BOOKING_PROMOTED` events
- Listens to `payment.events` for `PAYMENT_REFUNDED` notifications

---

## 21. Scheduled Jobs (Phase 6)

Phase 6 adds a scheduling layer using Spring's `@Scheduled` with a `ThreadPoolTaskScheduler` (4 threads).

### Job Overview

| Job | Schedule | Module | What It Does |
|-----|----------|--------|-------------|
| **BookingCleanupJob** | Every 60s | booking | Fails PAYMENT_PENDING bookings past 10-min timeout, restores seat inventory, publishes BOOKING_FAILED event |
| **TrainRunGenerationJob** | 2:00 AM daily | booking | Auto-generates TrainRun + SeatInventory for the next 7 days using all active schedules |
| **SearchIndexRefreshJob** | 3:30 AM daily | train | Full ES reindex as nightly safety net (Kafka handles real-time indexing) |
| **StaleDataCleanupJob** | 4:00 AM daily | booking | Marks train runs older than 30 days as COMPLETED |

### Architecture Decisions

**Cross-module data access** — `TrainRunGenerationJob` (booking module) needs schedule/route/coach data from the train module. We use the **Dependency Inversion** pattern: `ScheduleDataProvider` interface in railway-common, implemented by `ScheduleDataProviderImpl` in railway-train. Same pattern as `SearchDataProvider`.

**ThreadPoolTaskScheduler** — Default Spring scheduler uses a single thread. If BookingCleanupJob runs long, it would block the cron jobs. A 4-thread pool prevents this.

**Idempotency** — `TrainRunGenerationJob` is idempotent via `existsByScheduleIdAndRunDate` check. Running it manually or having it fire twice is safe.

**Admin trigger endpoint** — `POST /api/v1/admin/scheduler/trigger/{jobName}` lets admins manually fire any job for testing or recovery.

### Key Files

```
railway-app/.../config/SchedulerConfig.java       ← ThreadPoolTaskScheduler (4 threads)
railway-app/.../controller/AdminSchedulerController.java ← Manual trigger endpoint
railway-booking/.../scheduler/BookingCleanupJob.java     ← Expired booking cleanup
railway-booking/.../scheduler/TrainRunGenerationJob.java ← Auto-generate train runs
railway-booking/.../scheduler/StaleDataCleanupJob.java   ← Mark old runs COMPLETED
railway-common/.../scheduler/ScheduleDataProvider.java   ← Cross-module schedule data interface
railway-train/.../scheduler/ScheduleDataProviderImpl.java ← Schedule data implementation
railway-train/.../search/scheduler/SearchIndexRefreshJob.java ← Nightly ES reindex
```

---

## 22. File Reference Guide

Quick reference to find any file:

```
docs/ARCHITECTURE.md                                    ← THIS FILE
docs/LEARNINGS.md                                       ← Technologies & concepts guide

pom.xml                                                 ← Parent Maven POM
docker/docker-compose.yml                               ← PostgreSQL + Redis + Kafka + ZK + Kafka UI + Elasticsearch + Kibana

railway-common/src/main/java/com/railway/common/
├── dto/ErrorResponse.java                              ← API error format
├── dto/PagedResponse.java                              ← Pagination wrapper
├── entity/BaseEntity.java                              ← JPA auditing (createdAt/updatedAt)
├── event/
│   ├── EventEnvelope.java                              ← Generic Kafka event wrapper
│   ├── BookingEvent.java                               ← Booking event payload
│   ├── PaymentEvent.java                               ← Payment event payload
│   └── TrainRunEvent.java                              ← Train run event payload (triggers ES indexing)
├── search/
│   └── SearchDataProvider.java                         ← Interface: cross-module data access (Dependency Inversion)
├── scheduler/
│   └── ScheduleDataProvider.java                       ← [Phase 6] Interface: schedule data for auto-generation
└── exception/
    ├── BusinessException.java                          ← Base exception
    ├── ResourceNotFoundException.java                  ← 404 errors
    ├── DuplicateResourceException.java                 ← 409 errors
    └── GlobalExceptionHandler.java                     ← Catches all exceptions

railway-user/src/main/java/com/railway/user/
├── entity/User.java                                    ← User JPA entity
├── entity/Role.java                                    ← PASSENGER, ADMIN
├── repository/UserRepository.java                      ← User DB queries
├── security/JwtProvider.java                           ← Create/validate JWT tokens
├── security/JwtAuthenticationFilter.java               ← Extract JWT from requests
├── service/AuthService.java                            ← Register, login, refresh
├── service/UserService.java                            ← Profile lookup
├── controller/AuthController.java                      ← /api/v1/auth/*
├── controller/UserController.java                      ← /api/v1/users/*
└── dto/*.java                                          ← Request/Response DTOs

railway-train/src/main/java/com/railway/train/
├── entity/Station.java                                 ← Station JPA entity
├── entity/Train.java                                   ← Train JPA entity
├── entity/Route.java + RouteStation.java               ← Route with stops
├── entity/Schedule.java                                ← Which days trains run
├── entity/Coach.java                                   ← Coach composition
├── entity/CoachType.java + TrainType.java              ← Enums
├── repository/*.java                                   ← DB queries for each entity
├── service/StationService.java                         ← Station CRUD + search
├── service/TrainService.java                           ← Train CRUD
├── service/RouteService.java                           ← Route with stops CRUD
├── service/ScheduleService.java                        ← Schedule CRUD
├── search/                                             ← [Phase 4] Elasticsearch search
│   ├── document/JourneyOptionDocument.java             ← ES document model (denormalized journey)
│   ├── document/CoachAvailability.java                 ← Nested: seats per coach type
│   ├── document/FareInfo.java                          ← Nested: fare per coach type
│   ├── repository/JourneyOptionSearchRepository.java   ← Spring Data ES repository
│   ├── service/JourneyDocumentBuilder.java             ← Denormalization engine
│   ├── service/TrainSearchService.java                 ← ES query builder + reindex
│   └── kafka/SearchIndexingConsumer.java               ← Kafka → ES indexing pipeline
├── controller/StationController.java                   ← /api/v1/stations/*
├── controller/TrainController.java                     ← /api/v1/trains/*
├── controller/RouteController.java                     ← /api/v1/routes/*, /api/v1/admin/schedules
├── controller/TrainSearchController.java               ← GET /api/v1/trains/search (public)
├── controller/AdminSearchController.java               ← POST /api/v1/admin/search/reindex
└── dto/*.java                                          ← Request/Response DTOs + JourneySearchResponse
railway-train/src/main/resources/elasticsearch/
├── journey-options-settings.json                       ← Edge ngram analyzer config
└── journey-options-mappings.json                       ← Field mappings (keyword, nested, etc.)

railway-booking/src/main/java/com/railway/booking/
├── entity/
│   ├── Booking.java                                    ← Booking JPA entity (PNR, status, @Version, cancellationReason)
│   ├── BookingPassenger.java                           ← Per-passenger details + seat/RAC/waitlist assignment
│   ├── SeatInventory.java                              ← Segment-based availability (@Version)
│   ├── TrainRun.java                                   ← Materialized train run (schedule + date)
│   └── BookingStatus.java                              ← Enum: INITIATED → CONFIRMED / RAC / WAITLISTED / CANCELLED / FAILED
├── repository/
│   ├── BookingRepository.java                          ← findByPnr, findExpired, by segment+status
│   ├── BookingPassengerRepository.java                 ← findByBookingId
│   ├── SeatInventoryRepository.java                    ← decrement/increment for seats, RAC, waitlist with @Version
│   └── TrainRunRepository.java                         ← findByTrainAndDate
├── redis/
│   ├── SeatLockManager.java                            ← Distributed locking via Lua scripts
│   ├── AvailabilityCache.java                          ← Write-through availability cache
│   ├── PnrCache.java                                   ← Cache-aside PNR lookup
│   └── IdempotencyStore.java                           ← Duplicate request prevention
├── kafka/
│   ├── BookingEventPublisher.java                      ← Publishes booking lifecycle events (incl. CANCELLED, PROMOTED)
│   ├── PaymentEventConsumer.java                       ← Consumes payment events → confirms/fails bookings
│   ├── WaitlistPromotionConsumer.java                  ← [Phase 5] Promotes RAC→CONFIRMED, WAITLISTED→RAC on cancellation
│   └── TrainRunEventPublisher.java                     ← Publishes TRAIN_RUN_CREATED → ES indexing
├── search/
│   └── SearchDataProviderImpl.java                     ← Implements SearchDataProvider (cross-module)
├── service/
│   ├── BookingService.java                             ← THE ORCHESTRATOR: lock→book→cache→publish (+ RAC/Waitlist paths)
│   ├── CancellationService.java                        ← [Phase 5] Cancel booking, restore inventory, trigger event chain
│   ├── SeatAvailabilityService.java                    ← Redis-first availability check
│   ├── PnrStatusService.java                           ← Redis-first PNR lookup
│   ├── TrainRunService.java                            ← Schedule → TrainRun materialization
│   └── PnrGenerator.java                              ← "PNR" + 7 random digits
├── ratelimit/
│   ├── RateLimit.java                                  ← Custom @RateLimit annotation
│   ├── RateLimitInterceptor.java                       ← Redis sliding window enforcement
│   └── BookingWebConfig.java                           ← Register interceptor on /api/v1/**
├── controller/
│   ├── BookingController.java                          ← POST /bookings, GET /bookings/{pnr}, POST /bookings/{pnr}/cancel
│   ├── AvailabilityController.java                     ← GET /availability
│   ├── PnrController.java                              ← GET /pnr/{pnr}
│   └── AdminBookingController.java                     ← POST /admin/train-runs/generate
└── dto/
    ├── BookingRequest.java                             ← Booking + passengers input
    ├── BookingResponse.java                            ← Booking + passengers output
    ├── CancellationRequest.java                        ← [Phase 5] Optional cancellation reason
    ├── CancellationResponse.java                       ← [Phase 5] PNR, refund status, timestamp
    ├── SeatAvailabilityResponse.java                   ← Available seats for a segment
    ├── PnrStatusResponse.java                          ← PNR + passenger statuses
    └── GenerateTrainRunsRequest.java                   ← Admin: trainId + date range

railway-payment/src/main/java/com/railway/payment/
├── entity/
│   ├── Payment.java                                    ← Payment JPA entity (+ refundTransactionId)
│   └── PaymentStatus.java                              ← Enum: INITIATED → SUCCESS / FAILED / REFUNDED
├── repository/PaymentRepository.java                   ← findByBookingId, findByPnr
├── gateway/MockPaymentGateway.java                     ← Simulates payment (90% success) + refund (95% success)
├── kafka/
│   ├── PaymentEventPublisher.java                      ← Publishes PAYMENT_SUCCESS/FAILED/REFUNDED
│   └── BookingCancelledConsumer.java                   ← [Phase 5] Listens for BOOKING_CANCELLED → triggers refund
├── service/PaymentService.java                         ← Payment orchestrator + initiateRefund
├── controller/PaymentController.java                   ← /api/v1/payments/*
└── dto/
    ├── PaymentRequest.java                             ← bookingId + paymentMethod
    └── PaymentResponse.java                            ← Payment status + gateway info

railway-notification/src/main/java/com/railway/notification/
└── kafka/NotificationConsumer.java                     ← [Phase 5] @RetryableTopic, handles all booking + payment events

railway-app/src/main/java/com/railway/app/
├── RailwayApplication.java                             ← Main class
└── config/
    ├── SecurityConfig.java                             ← URL access rules, JWT filter
    ├── SwaggerConfig.java                              ← API documentation (Swagger UI)
    ├── RedisConfig.java                                ← Redis JSON serialization config
    ├── SchedulerConfig.java                            ← [Phase 6] ThreadPoolTaskScheduler (4 threads)
    └── KafkaConfig.java                                ← Topic creation (8+ topics: booking, payment, notification, train + DLTs)

railway-app/src/main/resources/
├── application.yml                                     ← All configuration (DB, JWT, Redis, Kafka, Elasticsearch)
└── db/migration/
    ├── V1__create_users.sql                            ← Users table
    ├── V2__create_stations_trains.sql                  ← Stations + Trains tables
    ├── V3__create_routes_schedules.sql                 ← Routes, RouteStations, Schedules, TrainRuns
    ├── V4__create_coaches_seats.sql                    ← Coaches + SeatInventory tables
    ├── V5__create_bookings.sql                         ← Bookings, Passengers, SeatAllocations
    ├── V6__create_payments.sql                         ← Payments table
    └── V7__cancellation_waitlist.sql                   ← [Phase 5] cancellation_reason, refund_transaction_id, indexes
```

---

## How to Run

```bash
# 1. Start all infrastructure (PostgreSQL + Redis + Kafka + ZK + Kafka UI + Elasticsearch + Kibana)
docker compose -f docker/docker-compose.yml up -d

# 2. Verify all services are healthy (should show 7 services)
docker compose -f docker/docker-compose.yml ps

# 3. Build the project
mvn clean package -DskipTests

# 4. Run the application
mvn spring-boot:run -pl railway-app

# 5. Open Swagger UI — test ALL APIs from the browser
# http://localhost:8080/swagger-ui.html

# 6. Open Kafka UI — see topics and messages
# http://localhost:8090

# 7. Open Kibana — inspect Elasticsearch indices and run queries
# http://localhost:5601
# Dev Tools → GET journey_options/_search  (see indexed journey documents)

# 8. Check Elasticsearch cluster health
# curl http://localhost:9200/_cluster/health?pretty

# 9. Check Redis keys
docker exec -it railway-redis redis-cli KEYS '*'

# 10. Connect to PostgreSQL
docker exec -it railway-postgres psql -U railway railway_booking
```
