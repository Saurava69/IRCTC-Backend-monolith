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
15. [What's Coming Next (Phase 2-6)](#15-whats-coming-next)
16. [File Reference Guide](#16-file-reference-guide)

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
                    │    ├── Payment Module (pay, refund)         [P3] │
                    │    └── Notification Module (email/SMS)      [P5] │
                    │                                                  │
                    ├──────────── Talks To ────────────────────────────┤
                    │                                                  │
                    │  PostgreSQL ──── Primary database (write)        │
                    │  Redis ────────── Caching, distributed locks [P2]│
                    │  Kafka ────────── Event streaming           [P3] │
                    │  Elasticsearch ── Search & read model       [P4] │
                    │                                                  │
                    │  [P1] = Phase 1 (done), [P2-P6] = coming next   │
                    └──────────────────────────────────────────────────┘
```

**Phase 1 (current)** sets up the foundation: project structure, database schema, authentication, and CRUD APIs for users/trains/stations/routes.

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
- **NO module depends on another module directly** (no `railway-booking` → `railway-train`)
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

### railway-booking, railway-payment, railway-notification

**Purpose**: Stubs for now. Each has only a `@Configuration` class so Maven can build them. Real implementation comes in Phases 2-5.

### railway-app

**Purpose**: The assembly module. Contains NO business logic — only configuration and infrastructure.

| File | What it does |
|------|-------------|
| `RailwayApplication.java` | Spring Boot main class. `@SpringBootApplication(scanBasePackages = "com.railway")` tells Spring to scan ALL modules |
| `SecurityConfig.java` | URL access rules, JWT filter chain, password encoder, auth manager |
| `SwaggerConfig.java` | OpenAPI documentation with JWT auth support |
| `application.yml` | All configuration: database URL, JWT settings, Flyway, etc. |
| `V1-V4 migrations` | SQL files that create all database tables |

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

## 15. What's Coming Next

| Phase | What | Key Concept You'll Learn |
|-------|------|-------------------------|
| **Phase 2** (Week 3-4) | Booking engine + Redis | **Distributed locking** — how to prevent two users from booking the same seat. **Caching** — make PNR lookup 100x faster. **Rate limiting** — prevent bots from flooding the booking API. |
| **Phase 3** (Week 5-6) | Payments + Kafka | **Event-driven architecture** — booking confirmation happens asynchronously via events. **Exactly-once delivery** — payments must never be processed twice. |
| **Phase 4** (Week 7-8) | Elasticsearch + CQRS | **CQRS pattern** — writes go to PostgreSQL, reads come from Elasticsearch. **Eventual consistency** — the search index lags behind by milliseconds. |
| **Phase 5** (Week 9-10) | Waitlist, cancellations, notifications | **Event choreography** — one cancellation triggers: refund + waitlist promotion + notification, all via events. |
| **Phase 6** (Week 11-12) | Testing, observability, resilience | **Integration tests** — test with real PostgreSQL/Redis/Kafka in Docker. **Circuit breaker** — gracefully handle payment gateway outages. |

---

## 16. File Reference Guide

Quick reference to find any file:

```
docs/ARCHITECTURE.md                                    ← THIS FILE

pom.xml                                                 ← Parent Maven POM
docker/docker-compose.yml                               ← PostgreSQL container

railway-common/src/main/java/com/railway/common/
├── dto/ErrorResponse.java                              ← API error format
├── dto/PagedResponse.java                              ← Pagination wrapper
├── entity/BaseEntity.java                              ← JPA auditing (createdAt/updatedAt)
├── event/EventEnvelope.java                            ← Kafka event wrapper
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
├── controller/StationController.java                   ← /api/v1/stations/*
├── controller/TrainController.java                     ← /api/v1/trains/*
├── controller/RouteController.java                     ← /api/v1/routes/*, /api/v1/admin/schedules
└── dto/*.java                                          ← Request/Response DTOs

railway-app/src/main/java/com/railway/app/
├── RailwayApplication.java                             ← Main class
└── config/
    ├── SecurityConfig.java                             ← URL access rules, JWT filter
    └── SwaggerConfig.java                              ← API documentation

railway-app/src/main/resources/
├── application.yml                                     ← All configuration
└── db/migration/
    ├── V1__create_users.sql                            ← Users table
    ├── V2__create_stations_trains.sql                  ← Stations + Trains tables
    ├── V3__create_routes_schedules.sql                 ← Routes, RouteStations, Schedules, TrainRuns
    └── V4__create_coaches_seats.sql                    ← Coaches + SeatInventory tables
```

---

## How to Run

```bash
# 1. Start PostgreSQL
docker compose -f docker/docker-compose.yml up -d

# 2. Build the project
mvn clean package

# 3. Run the application
mvn spring-boot:run -pl railway-app

# 4. Open Swagger UI
# http://localhost:8080/swagger-ui.html

# 5. Test: Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"secret123","fullName":"Test User"}'
```
