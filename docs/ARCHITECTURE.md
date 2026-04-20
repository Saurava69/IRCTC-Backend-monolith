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
17. [What's Coming Next (Phase 3-6)](#17-whats-coming-next)
18. [File Reference Guide](#18-file-reference-guide)

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

### railway-payment, railway-notification

**Purpose**: Stubs for now. Each has only a `@Configuration` class so Maven can build them. Real implementation comes in Phases 3 and 5.

### railway-app

**Purpose**: The assembly module. Contains NO business logic — only configuration and infrastructure.

| File | What it does |
|------|-------------|
| `RailwayApplication.java` | Spring Boot main class. `@SpringBootApplication(scanBasePackages = "com.railway")` tells Spring to scan ALL modules |
| `SecurityConfig.java` | URL access rules, JWT filter chain, password encoder, auth manager |
| `SwaggerConfig.java` | OpenAPI documentation with JWT auth support |
| `RedisConfig.java` | RedisTemplate with Jackson JSON serializer for object storage |
| `application.yml` | All configuration: database, JWT, Redis, rate limits, cache TTLs |
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

## 17. What's Coming Next

| Phase | What | Key Concept You'll Learn |
|-------|------|-------------------------|
| **Phase 3** (Week 5-6) | Payments + Kafka | **Event-driven architecture** — booking confirmation happens asynchronously via events. **Exactly-once delivery** — payments must never be processed twice. |
| **Phase 4** (Week 7-8) | Elasticsearch + CQRS | **CQRS pattern** — writes go to PostgreSQL, reads come from Elasticsearch. **Eventual consistency** — the search index lags behind by milliseconds. |
| **Phase 5** (Week 9-10) | Waitlist, cancellations, notifications | **Event choreography** — one cancellation triggers: refund + waitlist promotion + notification, all via events. |
| **Phase 6** (Week 11-12) | Testing, observability, resilience | **Integration tests** — test with real PostgreSQL/Redis/Kafka in Docker. **Circuit breaker** — gracefully handle payment gateway outages. |

---

## 18. File Reference Guide

Quick reference to find any file:

```
docs/ARCHITECTURE.md                                    ← THIS FILE

pom.xml                                                 ← Parent Maven POM
docker/docker-compose.yml                               ← PostgreSQL + Redis containers

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

railway-booking/src/main/java/com/railway/booking/
├── entity/
│   ├── Booking.java                                    ← Booking JPA entity (PNR, status, @Version)
│   ├── BookingPassenger.java                           ← Per-passenger details + seat assignment
│   ├── SeatInventory.java                              ← Segment-based availability (@Version)
│   ├── TrainRun.java                                   ← Materialized train run (schedule + date)
│   └── BookingStatus.java                              ← Enum: INITIATED → CONFIRMED / FAILED
├── repository/
│   ├── BookingRepository.java                          ← findByPnr, findExpired, by user
│   ├── BookingPassengerRepository.java                 ← findByBookingId
│   ├── SeatInventoryRepository.java                    ← decrement/increment with @Version
│   └── TrainRunRepository.java                         ← findByTrainAndDate
├── redis/
│   ├── SeatLockManager.java                            ← Distributed locking via Lua scripts
│   ├── AvailabilityCache.java                          ← Write-through availability cache
│   ├── PnrCache.java                                   ← Cache-aside PNR lookup
│   └── IdempotencyStore.java                           ← Duplicate request prevention
├── service/
│   ├── BookingService.java                             ← THE ORCHESTRATOR: lock→book→cache
│   ├── SeatAvailabilityService.java                    ← Redis-first availability check
│   ├── PnrStatusService.java                           ← Redis-first PNR lookup
│   ├── TrainRunService.java                            ← Schedule → TrainRun materialization
│   └── PnrGenerator.java                              ← "PNR" + 7 random digits
├── ratelimit/
│   ├── RateLimit.java                                  ← Custom @RateLimit annotation
│   ├── RateLimitInterceptor.java                       ← Redis sliding window enforcement
│   └── BookingWebConfig.java                           ← Register interceptor on /api/v1/**
├── controller/
│   ├── BookingController.java                          ← POST /bookings, GET /bookings/{pnr}
│   ├── AvailabilityController.java                     ← GET /availability
│   ├── PnrController.java                              ← GET /pnr/{pnr}
│   └── AdminBookingController.java                     ← POST /admin/train-runs/generate
└── dto/
    ├── BookingRequest.java                             ← Booking + passengers input
    ├── BookingResponse.java                            ← Booking + passengers output
    ├── SeatAvailabilityResponse.java                   ← Available seats for a segment
    ├── PnrStatusResponse.java                          ← PNR + passenger statuses
    └── GenerateTrainRunsRequest.java                   ← Admin: trainId + date range

railway-app/src/main/java/com/railway/app/
├── RailwayApplication.java                             ← Main class
└── config/
    ├── SecurityConfig.java                             ← URL access rules, JWT filter
    ├── SwaggerConfig.java                              ← API documentation
    └── RedisConfig.java                                ← Redis JSON serialization config

railway-app/src/main/resources/
├── application.yml                                     ← All configuration
└── db/migration/
    ├── V1__create_users.sql                            ← Users table
    ├── V2__create_stations_trains.sql                  ← Stations + Trains tables
    ├── V3__create_routes_schedules.sql                 ← Routes, RouteStations, Schedules, TrainRuns
    ├── V4__create_coaches_seats.sql                    ← Coaches + SeatInventory tables
    └── V5__create_bookings.sql                         ← Bookings, Passengers, SeatAllocations
```

---

## How to Run

```bash
# 1. Start PostgreSQL + Redis
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

# 6. Check Redis keys (after some bookings)
docker exec -it railway-redis redis-cli KEYS '*'
```
