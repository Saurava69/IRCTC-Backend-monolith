# Railway Ticket Booking — Learnings & Concepts Guide

> Everything you need to understand about the technologies, patterns, and best practices used in this project.
> Each section explains **what**, **why**, and **how** — with code from our codebase and links to learn more.

---

## Table of Contents

1. [Spring Boot & Spring Framework](#1-spring-boot--spring-framework)
2. [Maven Multi-Module Project](#2-maven-multi-module-project)
3. [PostgreSQL & Database Design](#3-postgresql--database-design)
4. [Flyway Database Migrations](#4-flyway-database-migrations)
5. [JPA & Hibernate](#5-jpa--hibernate)
6. [JWT Authentication](#6-jwt-authentication)
7. [Spring Security](#7-spring-security)
8. [Redis — The Speed Layer](#8-redis--the-speed-layer)
9. [Distributed Locking](#9-distributed-locking)
10. [Caching Patterns](#10-caching-patterns)
11. [Rate Limiting](#11-rate-limiting)
12. [Idempotency](#12-idempotency)
13. [Optimistic Locking](#13-optimistic-locking)
14. [DTO Pattern & Validation](#14-dto-pattern--validation)
15. [Exception Handling](#15-exception-handling)
16. [Docker & Containerization](#16-docker--containerization)
17. [API Design Best Practices](#17-api-design-best-practices)
18. [Concurrency Control — The Full Picture](#18-concurrency-control--the-full-picture)
19. [Java Records & Modern Java](#19-java-records--modern-java)
20. [Lombok](#20-lombok)
21. [Apache Kafka & Event-Driven Architecture](#21-apache-kafka--event-driven-architecture)
22. [Payment Processing Patterns](#22-payment-processing-patterns)
23. [Real Bugs We Hit & Fixed (Phase 1-3)](#23-real-bugs-we-hit--fixed-phase-1-3)
24. [What's Coming — Elasticsearch, CQRS, Waitlist](#24-whats-coming--elasticsearch-cqrs-waitlist)
25. [Learning Resources](#25-learning-resources)

---

## 1. Spring Boot & Spring Framework

### What is Spring Boot?

Spring Boot is an opinionated framework built on top of the Spring Framework. It auto-configures most things so you don't write boilerplate XML/config.

**Key concepts in our project:**

**Dependency Injection (DI)** — The core idea. Instead of creating objects yourself, Spring creates them and "injects" them where needed.

```java
// BAD: You manually create dependencies
public class BookingService {
    private BookingRepository repo = new BookingRepository(); // tightly coupled
}

// GOOD: Spring injects it (what we do)
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository; // Spring provides this
}
```

`@RequiredArgsConstructor` (Lombok) generates a constructor with all `final` fields. Spring sees the constructor and auto-injects the matching bean.

**Annotations you'll see everywhere:**

| Annotation | What it does |
|-----------|-------------|
| `@SpringBootApplication` | Enables auto-config, component scanning, and Spring Boot features |
| `@RestController` | Marks a class as a REST API controller (returns JSON, not HTML) |
| `@Service` | Marks business logic classes — Spring manages their lifecycle |
| `@Repository` | Marks database access classes — Spring adds exception translation |
| `@Component` | Generic "please manage this bean" — `@Service` and `@Repository` are specializations |
| `@Configuration` | Declares a class that provides `@Bean` definitions |
| `@Bean` | A method that returns an object Spring should manage |
| `@Value("${app.jwt.secret}")` | Injects a value from `application.yml` |

**Auto-configuration** — When you add `spring-boot-starter-data-jpa` to your POM, Spring Boot automatically:
1. Creates a `DataSource` from your `application.yml` database URL
2. Creates an `EntityManagerFactory` for JPA
3. Creates a `TransactionManager`
4. Scans for `@Entity` and `@Repository` classes

You write zero configuration code for all of this.

**Profiles** — Not used yet, but important to know. You can have `application-dev.yml` and `application-prod.yml` with different database URLs, and activate them with `spring.profiles.active=dev`.

### Learn More
- [Spring Boot Reference Docs](https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/)
- [Baeldung — Spring Boot Tutorial](https://www.baeldung.com/spring-boot)
- [Spring Framework Core — IoC Container](https://docs.spring.io/spring-framework/reference/core.html)

---

## 2. Maven Multi-Module Project

### What is Maven?

Maven is a build tool. It downloads dependencies, compiles code, runs tests, and packages JARs. The `pom.xml` file is its configuration.

### Why Multi-Module?

Our project has 7 modules. Each module is its own JAR with its own `pom.xml`. Why not one big project?

```
railway-ticket-booking/          ← Parent POM (no code, just configuration)
├── railway-common/              ← Shared code (exceptions, DTOs, base entity)
├── railway-user/                ← Authentication, user management
├── railway-train/               ← Train, station, route management
├── railway-booking/             ← THE CORE: booking, availability, Redis
├── railway-payment/             ← Payment processing (Phase 3)
├── railway-notification/        ← Notifications (Phase 5)
└── railway-app/                 ← Assembly: configs, migrations, main class
```

**Benefits:**
1. **Enforced boundaries** — `railway-train` can't accidentally import `railway-booking` classes
2. **Independent compilation** — Change booking code? Only booking module recompiles
3. **Clear ownership** — Each module has a clear responsibility
4. **Future extraction** — Any module can become a microservice later

### How the Parent POM Works

```xml
<!-- Parent POM manages versions for ALL modules -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.railway</groupId>
            <artifactId>railway-common</artifactId>
            <version>${project.version}</version>  <!-- All modules share the same version -->
        </dependency>
        <!-- ... all internal modules + third-party libs ... -->
    </dependencies>
</dependencyManagement>
```

Child modules declare dependencies WITHOUT versions — the parent manages them:

```xml
<!-- In railway-booking/pom.xml -->
<dependency>
    <groupId>com.railway</groupId>
    <artifactId>railway-common</artifactId>
    <!-- No <version> tag! Parent POM provides it -->
</dependency>
```

**Why?** If 5 modules use the same library, you update the version in ONE place (parent POM), not 5 places. This prevents version conflicts.

### The Build Order

Maven reads `<modules>` in the parent POM and builds them in dependency order:

```
1. railway-common     (no dependencies)
2. railway-user       (depends on common)
3. railway-train      (depends on common)
4. railway-booking    (depends on common + user)
5. railway-payment    (depends on common)
6. railway-notification (depends on common)
7. railway-app        (depends on ALL modules — the final assembly)
```

### Annotation Processors (Lombok + MapStruct)

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>lombok</path>          <!-- FIRST: generates getters/setters -->
            <path>mapstruct-processor</path>  <!-- SECOND: reads generated code -->
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Order matters! Lombok generates getters/setters at compile time. MapStruct then reads those generated methods to create mapper implementations. If reversed, MapStruct wouldn't see the methods.

### Learn More
- [Maven in 5 Minutes](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
- [Baeldung — Maven Multi-Module](https://www.baeldung.com/maven-multi-module)
- [Maven Dependency Management vs Dependencies](https://www.baeldung.com/maven-dependencymanagement-vs-dependencies-tags)

---

## 3. PostgreSQL & Database Design

### Why PostgreSQL?

PostgreSQL is the most feature-rich open-source relational database. We use it because:
- **ACID transactions** — Booking + passenger creation either BOTH succeed or BOTH fail
- **Rich data types** — `TIMESTAMPTZ`, `DECIMAL(12,2)`, `BIGSERIAL`
- **Advanced indexing** — GIN indexes for full-text search, B-tree for everything else
- **Advisory locks** — Built-in distributed locking (we use Redis instead, but it's there)

### Key Schema Design Decisions

**1. Segment-Based Seat Inventory**

This is the most important design decision. Railway seats aren't like flight seats.

```
Route: Delhi → Jaipur → Ahmedabad → Mumbai

Passenger A books Delhi → Ahmedabad (sits in seat, gets off at Ahmedabad)
Passenger B can book Ahmedabad → Mumbai (same seat is now free!)
```

The `seat_inventory` table tracks availability per segment:

```sql
-- Each row = one (train_run, coach_type, from_station, to_station) combination
CREATE TABLE seat_inventory (
    train_run_id    BIGINT,
    coach_type      VARCHAR(20),
    from_station_id BIGINT,
    to_station_id   BIGINT,
    total_seats     INT,
    available_seats INT,
    version         BIGINT DEFAULT 0,  -- Optimistic locking
    UNIQUE(train_run_id, coach_type, from_station_id, to_station_id)
);
```

For a route with 4 stations, each coach type gets 3 inventory rows (consecutive pairs):
- Delhi → Jaipur
- Jaipur → Ahmedabad
- Ahmedabad → Mumbai

**2. Surrogate Keys (BIGSERIAL)**

Every table uses `id BIGSERIAL PRIMARY KEY` — an auto-incrementing 64-bit integer. Why not UUIDs?
- Faster B-tree indexing (8 bytes vs 16 bytes)
- Better page locality in PostgreSQL
- Simpler foreign key joins
- We generate UUIDs separately where needed (PNR, idempotency keys)

**3. Indexes**

```sql
-- We create indexes on columns used in WHERE clauses
CREATE INDEX idx_bookings_pnr ON bookings(pnr);           -- PNR lookups
CREATE INDEX idx_bookings_user ON bookings(user_id);       -- "My bookings"
CREATE INDEX idx_bookings_status ON bookings(booking_status); -- Expired booking cleanup

-- Composite index for the most common query
CREATE INDEX idx_seat_inv_lookup ON seat_inventory(
    train_run_id, coach_type, from_station_id, to_station_id
);

-- Full-text search on station names
CREATE INDEX idx_stations_name ON stations USING gin(to_tsvector('english', name));
```

**Without indexes:** PostgreSQL scans every row (sequential scan). A table with 1M rows = 1M comparisons.
**With indexes:** PostgreSQL uses a B-tree to find the row in O(log n) — ~20 comparisons for 1M rows.

**4. Unique Constraints as Business Rules**

```sql
UNIQUE(schedule_id, run_date)     -- Can't generate the same train run twice
UNIQUE(train_id, coach_number)    -- Can't have two coaches with same number
UNIQUE(route_id, station_id)      -- A station appears once in a route
```

Unique constraints are enforced at the database level. Even if your application code has a bug, the database prevents invalid data.

### Learn More
- [PostgreSQL Official Tutorial](https://www.postgresql.org/docs/16/tutorial.html)
- [Use The Index, Luke — SQL Indexing](https://use-the-index-luke.com/) (excellent free book)
- [Designing Data-Intensive Applications — Ch. 2-3](https://dataintensive.net/) (the bible for data modeling)

---

## 4. Flyway Database Migrations

### The Problem

You have a database schema. You need to add a column. You run `ALTER TABLE` on your local DB. But your teammate doesn't know about the change. Production definitely doesn't.

### The Solution: Version-Controlled Migrations

Flyway tracks schema changes as numbered SQL files:

```
db/migration/
├── V1__create_users.sql
├── V2__create_stations_trains.sql
├── V3__create_routes_schedules.sql
├── V4__create_coaches_seats.sql
└── V5__create_bookings.sql
```

**How it works:**
1. Flyway creates a `flyway_schema_history` table in your database
2. On app startup, it checks which migrations have been applied
3. It runs any new migrations in order (V1, V2, V3...)
4. Each migration is recorded in the history table with a checksum

**Rules:**
- **Never modify an applied migration** — Flyway checksums each file. If you change V3, Flyway throws an error
- **Always add new migrations** — Need to add a column? Create `V6__add_column_x.sql`
- **Naming convention**: `V{number}__{description}.sql` (double underscore!)

**Our configuration:**

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Hibernate ONLY validates schema matches entities
  flyway:
    enabled: true  # Flyway manages all schema changes
```

`ddl-auto: validate` means Hibernate checks that your Java entities match the database tables but NEVER modifies the schema. Flyway is the only thing allowed to change the database structure.

### Learn More
- [Flyway Documentation](https://documentation.red-gate.com/flyway)
- [Baeldung — Database Migrations with Flyway](https://www.baeldung.com/database-migrations-with-flyway)

---

## 5. JPA & Hibernate

### What is JPA? What is Hibernate?

- **JPA** (Jakarta Persistence API) — A specification (interface). It defines annotations like `@Entity`, `@Table`, `@Column`
- **Hibernate** — An implementation of JPA. It's the actual code that translates your Java objects to SQL queries

Think of JPA as the "rules" and Hibernate as the "engine" that follows those rules.

### How We Use JPA

**Entity = Database Table**

```java
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB auto-generates ID
    private Long id;

    @Column(unique = true, length = 15)
    private String pnr;

    @Enumerated(EnumType.STRING)  // Store "CONFIRMED" not 3
    private BookingStatus bookingStatus;

    @Version  // Optimistic locking (Section 13)
    private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingPassenger> passengers = new ArrayList<>();
}
```

**Repository = Database Queries**

Spring Data JPA generates implementations from method names:

```java
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPnr(String pnr);
    // Spring generates: SELECT * FROM bookings WHERE pnr = ?

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    // Spring generates: SELECT * FROM bookings WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?
}
```

**Custom queries with `@Query`:**

```java
@Modifying
@Query("UPDATE SeatInventory s SET s.availableSeats = s.availableSeats - :count, " +
       "s.version = s.version + 1 WHERE s.id = :id AND s.availableSeats >= :count " +
       "AND s.version = :version")
int decrementAvailableSeats(@Param("id") Long id, @Param("count") int count,
                            @Param("version") Long version);
// Returns number of rows updated. 0 = someone else modified it first!
```

### JPA Auditing — Automatic Timestamps

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;    // Set automatically on INSERT

    @LastModifiedDate
    private Instant updatedAt;    // Set automatically on every UPDATE
}
```

Every entity that extends `BaseEntity` gets automatic timestamps. No manual `setCreatedAt()` calls needed.

### Important JPA Settings in Our Config

```yaml
spring:
  jpa:
    open-in-view: false  # CRITICAL: prevents the "Open Session in View" anti-pattern
    properties:
      hibernate:
        jdbc.batch_size: 25        # Batch 25 INSERTs into one DB roundtrip
        order_inserts: true        # Group inserts by entity type for batching
        order_updates: true        # Same for updates
```

**`open-in-view: false`** — By default, Spring keeps a Hibernate session open for the entire HTTP request. This means lazy-loaded relationships work in controllers and views. Sounds convenient, but it causes N+1 query problems and hides performance issues. We disable it: if you need data, load it in the service layer explicitly.

### Learn More
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Baeldung — JPA and Hibernate Guide](https://www.baeldung.com/learn-jpa-hibernate)
- [Vlad Mihalcea's Blog](https://vladmihalcea.com/) (the best JPA/Hibernate resource)

---

## 6. JWT Authentication

### What is JWT?

JWT (JSON Web Token) is a self-contained token format. The server creates a token with user info, signs it, and sends it to the client. The client sends it back on every request. The server verifies the signature — no session storage needed.

### JWT Structure

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwidXNlcklkIjoxLCJyb2xlIjoiUEFTU0VOR0VSIiwiZXhwIjoxNzE0NjAwMDAwfQ.signature_here
|________________________|______________________________________________________________________________|_______________|
        HEADER                                        PAYLOAD                                           SIGNATURE
```

- **Header**: Algorithm (HS256) and token type (JWT)
- **Payload**: Claims — user email, ID, role, expiry time
- **Signature**: HMAC-SHA256(header + payload, secret_key)

**Important**: The payload is NOT encrypted — it's just Base64 encoded. Anyone can read it. The signature only proves it wasn't tampered with.

### Our Implementation

```java
// JwtProvider.java — Creating tokens
private String buildToken(User user, long expirationMs) {
    return Jwts.builder()
            .subject(user.getEmail())              // Who is this?
            .claim("userId", user.getId())         // Custom claim
            .claim("role", user.getRole().name())  // PASSENGER or ADMIN
            .issuedAt(new Date())                  // When was it created?
            .expiration(new Date(System.currentTimeMillis() + expirationMs))  // When does it expire?
            .signWith(key)                         // Sign with HMAC-SHA256
            .compact();                            // Build the string
}
```

**Two tokens:**
- **Access token** (1 hour) — Sent with every API request in `Authorization: Bearer <token>`
- **Refresh token** (24 hours) — Used only to get a new access token when the old one expires

**Why two tokens?** If an access token is stolen, the attacker has only 1 hour. The refresh token is sent less frequently (only for refresh calls), reducing exposure.

### Token Validation Flow

```
Every HTTP request:
  1. JwtAuthenticationFilter extracts "Bearer <token>" from Authorization header
  2. JwtProvider.validateToken() verifies signature and checks expiry
  3. If valid: load User from DB by email → set SecurityContext
  4. If invalid/missing: continue without auth → Spring Security returns 401
```

### Security Considerations

- **Secret key** must be ≥256 bits for HS256 (we use a long string in application.yml — in production, use environment variables or a vault)
- **Never store JWTs in localStorage** (XSS vulnerable) — use httpOnly cookies for browsers
- **Token revocation** is hard with JWTs (they're stateless). Our plan: Redis blacklist for logout (Phase 3+)

### Learn More
- [jwt.io — JWT Debugger](https://jwt.io/) (paste a token to see its contents)
- [Auth0 — Introduction to JWTs](https://auth0.com/learn/json-web-tokens)
- [OWASP — JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)

---

## 7. Spring Security

### How It Works

Spring Security is a chain of filters that run BEFORE your controller code:

```
HTTP Request
    → SecurityFilterChain
        → JwtAuthenticationFilter (our custom filter)
            → AuthorizationFilter (checks roles/permissions)
                → Your Controller Method
```

### Our Configuration

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())             // No CSRF for stateless REST APIs
        .sessionManagement(session ->
            session.sessionCreationPolicy(STATELESS))  // No server-side sessions
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()     // Public
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN") // Admin only
            .anyRequest().authenticated())                       // Everything else: login required
        .addFilterBefore(jwtAuthFilter,
            UsernamePasswordAuthenticationFilter.class);  // Our JWT filter runs first
    return http.build();
}
```

**Why disable CSRF?** CSRF (Cross-Site Request Forgery) protection is for browser-based sessions with cookies. We use JWT tokens in headers — CSRF attacks don't apply.

**Why STATELESS sessions?** We don't want Spring to create HTTP sessions. Each request carries its own authentication (the JWT). This makes the app horizontally scalable — any server can handle any request.

**`@PreAuthorize`** — Method-level security:

```java
@PostMapping("/train-runs/generate")
@PreAuthorize("hasRole('ADMIN')")  // Only ADMIN users can call this
public ResponseEntity<?> generateTrainRuns(...) { }
```

**`@AuthenticationPrincipal`** — Get the current user:

```java
@PostMapping
public ResponseEntity<BookingResponse> initiateBooking(
        @RequestBody BookingRequest request,
        @AuthenticationPrincipal User user) {  // Spring injects the authenticated User
    bookingService.initiateBooking(request, user.getId());
}
```

### Password Hashing

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();  // One-way hash with salt
}
```

BCrypt is slow **on purpose**. A brute-force attacker trying billions of passwords is slowed down dramatically. Each hash includes a random salt, so two users with the same password have different hashes.

### Learn More
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Baeldung — Spring Security Series](https://www.baeldung.com/security-spring)
- [OWASP — Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

---

## 8. Redis — The Speed Layer

### What is Redis?

Redis is an **in-memory** key-value store. Think of it as a giant HashMap that:
- Lives outside your application (shared across all instances)
- Supports complex data structures (strings, hashes, sorted sets, lists)
- Has built-in TTL (keys auto-delete after a time)
- Is single-threaded (no concurrency bugs inside Redis)
- Runs operations in ~0.1ms (vs ~2-5ms for PostgreSQL)

### Our Redis Setup

```yaml
# docker-compose.yml
redis:
  image: redis:7-alpine
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
```

**`--maxmemory 256mb`**: Caps memory usage. Redis is in-memory — without a limit, it could consume all RAM.

**`--maxmemory-policy allkeys-lru`**: When memory is full, evict the Least Recently Used key. Perfect for a cache — rarely-accessed data gets evicted first.

### Redis Data Types We Use

| Type | Where | Why |
|------|-------|-----|
| **String** | Seat locks, idempotency, availability cache, PNR cache | Simple key→value with TTL |
| **Sorted Set (ZSET)** | Rate limiting | Members sorted by score (timestamp) — efficient range queries |

### How We Connect to Redis

```java
// RedisConfig.java — Two templates for different use cases

// Template 1: For storing Java objects as JSON
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
    return template;
}

// Template 2: For simple string operations (Lua scripts, counters)
@Bean
public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
}
```

### Redis Key Naming Convention

We use a consistent naming pattern: `{purpose}:{identifier1}:{identifier2}`

```
seat-lock:42:FIRST_AC:1:2:booking-abc    → "1"              (lock exists)
seat-avail:42:FIRST_AC:1:2               → "67"             (67 seats available)
avail:42:FIRST_AC                         → "{json...}"      (cached response)
pnr:PNR1234567                            → "{json...}"      (cached PNR status)
rate-limit:booking:15                     → Sorted Set {...}  (user 15's recent requests)
idempotency:abc-123-xyz                   → "PROCESSING"      (request in flight)
```

### Why Not Just Use PostgreSQL for Everything?

| Operation | PostgreSQL | Redis |
|-----------|-----------|-------|
| Simple key lookup | ~2-5ms | ~0.1ms |
| Concurrent updates | Row lock contention | Single-threaded, no contention |
| Auto-expiry (TTL) | Need scheduled jobs | Built-in per key |
| Atomic check-and-set | Requires transactions | Lua scripts |

**Rule of thumb**: PostgreSQL = source of truth (durable). Redis = speed layer (ephemeral). If Redis crashes, we lose caches and locks, but no data is lost. PostgreSQL has the complete picture.

### Learn More
- [Redis University — Free Courses](https://university.redis.io/)
- [Redis Documentation](https://redis.io/docs/)
- [Redis in Action (book)](https://www.manning.com/books/redis-in-action)
- [Baeldung — Spring Data Redis](https://www.baeldung.com/spring-data-redis-tutorial)

---

## 9. Distributed Locking

### The Problem

Two users try to book the last seat at the same instant:

```
User A: reads availableSeats = 1 ✓
User B: reads availableSeats = 1 ✓  (same millisecond)
User A: decrements to 0, books seat ✓
User B: decrements to 0, books seat ✓  ← PROBLEM: seat doesn't exist!
```

This is a **race condition** — the result depends on timing.

### The Solution: Redis Distributed Lock with Lua Script

A Lua script runs **atomically** on Redis (no interruption):

```lua
-- SeatLockManager.java LOCK_SCRIPT
local available = tonumber(redis.call('GET', KEYS[2]) or '-1')
if available == -1 then return -1 end   -- availability not cached
local requested = tonumber(ARGV[1])
if available >= requested then
    redis.call('DECRBY', KEYS[2], requested)  -- Decrement atomically
    redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])  -- Set lock with TTL
    return 1  -- Success
end
return 0  -- Not enough seats
```

**Why Lua?** Individual Redis commands are atomic, but BETWEEN commands, another client could interleave. Lua scripts execute as a single atomic unit — no other command can run in the middle.

**Why TTL?** The user has 10 minutes to pay. If they abandon the booking, the lock auto-expires and seats become available again. No manual cleanup needed.

### Why Not Just Use Database Locks?

```sql
-- This works but holds a DB connection for the entire payment window
SELECT * FROM seat_inventory WHERE ... FOR UPDATE;
-- ... user takes 10 minutes to pay ...
-- Connection is BLOCKED the entire time!
```

With 1000 concurrent bookings, you'd need 1000 DB connections held open for 10 minutes each. Your connection pool has 20 connections. Everything grinds to a halt.

Redis locks are independent of DB connections. Lock a seat, release the DB connection, let the user pay at their leisure.

### Our Three-Layer Protection

```
Layer 1: Redis Lua script (fast, distributed)
    ↓ (if Redis is down, fall through)
Layer 2: PostgreSQL @Version optimistic lock (durable)
    ↓ (if version mismatch, release Redis lock + throw error)
Layer 3: DB unique constraints (last safety net)
```

No single layer is perfect. Together, they're rock-solid.

### Learn More
- [Redis — Distributed Locks (Redlock)](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Martin Kleppmann — How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html) (famous critique of Redlock)
- [Designing Data-Intensive Applications — Ch. 8](https://dataintensive.net/) (distributed systems fundamentals)

---

## 10. Caching Patterns

### Pattern 1: Write-Through (Availability Cache)

**Every write updates both DB and cache simultaneously.**

```
READ:  Client → Redis (hit?) → yes → return cached data
                              → no  → PostgreSQL → write to Redis → return

WRITE: Service → update PostgreSQL → update/evict Redis
```

```java
// SeatAvailabilityService.java
public SeatAvailabilityResponse getAvailability(Long trainRunId, String coachType) {
    // Step 1: Try Redis
    Optional<SeatAvailabilityResponse> cached = availabilityCache.get(trainRunId, coachType);
    if (cached.isPresent()) {
        log.debug("Availability cache HIT");
        return cached.get();
    }
    
    // Step 2: Cache miss → Query PostgreSQL
    log.debug("Availability cache MISS");
    List<SeatInventory> segments = seatInventoryRepository
            .findByTrainRunAndCoachType(trainRunId, coachType);
    
    // Step 3: Build response + write to cache
    SeatAvailabilityResponse response = buildResponse(segments);
    availabilityCache.put(trainRunId, coachType, response);
    return response;
}
```

**When to use**: Data that changes often AND is read very frequently. Stale data is costly (selling phantom seats).

### Pattern 2: Cache-Aside / Lazy Loading (PNR Cache)

**Only populate cache when data is requested. On writes, just evict.**

```
READ:  Client → Redis (hit?) → yes → return
                              → no  → PostgreSQL → store in Redis → return

WRITE: Service → update PostgreSQL → DELETE from Redis (not update)
       Next read will re-populate from fresh DB data
```

**When to use**: Data that is read often but changes infrequently. PNR status changes only on booking/cancellation events.

**Why evict instead of update?** PNR responses have nested passenger data. It's simpler to delete the stale entry and let the next read rebuild it from the DB, rather than surgically updating parts of a cached JSON object.

### Cache TTLs

| Cache | TTL | Why |
|-------|-----|-----|
| Availability | 5 minutes | Changes frequently (every booking). Short TTL = fresh data |
| PNR Status | 15 minutes | Changes rarely. Longer TTL = more cache hits |
| Seat Lock | 10 minutes | Payment window. Must match business requirement exactly |
| Idempotency | 24 hours | Prevent duplicate requests within a day |
| Rate Limit | Equals window | 60 seconds for per-minute limits |

**TTLs are safety nets.** Even with explicit cache eviction on writes, TTLs ensure stale data eventually disappears if an eviction fails.

### Learn More
- [AWS — Caching Strategies](https://docs.aws.amazon.com/whitepapers/latest/database-caching-strategies-using-redis/caching-patterns.html)
- [Caching Best Practices — Microsoft](https://learn.microsoft.com/en-us/azure/architecture/best-practices/caching)
- [Cache Stampede — What It Is and How to Prevent It](https://en.wikipedia.org/wiki/Cache_stampede)

---

## 11. Rate Limiting

### The Problem

Without rate limiting:
- A bot sends 10,000 booking requests per second
- Legitimate users can't get through
- Your database/Redis get overwhelmed
- You might process fraudulent bulk bookings

### Sliding Window Algorithm

We use Redis Sorted Sets (ZSET). Each member is a request timestamp:

```lua
-- RateLimitInterceptor.java — SLIDING_WINDOW_SCRIPT
local key = KEYS[1]
local now = tonumber(ARGV[1])         -- Current time in ms
local window = tonumber(ARGV[2])      -- Window size in seconds
local limit = tonumber(ARGV[3])       -- Max requests allowed

redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)  -- Remove expired entries
local count = redis.call('ZCARD', key)                        -- Count remaining

if count < limit then
    redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
    redis.call('EXPIRE', key, window)
    return limit - count - 1    -- Remaining requests
end
return -1                       -- Rate limited!
```

**Why Sorted Sets?** Each entry has a score (timestamp). `ZREMRANGEBYSCORE` efficiently removes old entries. `ZCARD` counts what's left. It's the perfect data structure for a time window.

**Why not a simple counter?** A fixed window counter resets at minute boundaries. A user could send 30 requests at 11:59:59 and 30 more at 12:00:01 — 60 requests in 2 seconds, all "within the limit." Sliding window doesn't have this boundary problem.

### Our Limits

```java
@GetMapping
@RateLimit(requests = 30, windowSeconds = 60, keyPrefix = "search")  // Search: 30/min
public ResponseEntity<?> checkAvailability(...) { }

@PostMapping
@RateLimit(requests = 5, windowSeconds = 60, keyPrefix = "booking")  // Booking: 5/min
public ResponseEntity<?> initiateBooking(...) { }
```

**Why different limits?** Searching is cheap (Redis cache hit). Booking is expensive (Redis lock + DB write + validation). Tighter limits on expensive operations protect the system.

### Response Headers

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 5        ← Your max
X-RateLimit-Remaining: 3    ← Requests left in this window
```

Clients can read these headers to throttle themselves before hitting the limit.

### Learn More
- [Stripe — Rate Limiters in Practice](https://stripe.com/blog/rate-limiters)
- [Redis — Rate Limiting Pattern](https://redis.io/glossary/rate-limiting/)
- [System Design — Rate Limiter](https://bytebytego.com/courses/system-design-interview/design-a-rate-limiter)

---

## 12. Idempotency

### The Problem

```
User clicks "Book Now"
    → Request sent to server
    → Network timeout (no response received)
    → User clicks "Book Now" again
    → Second request sent
    → Now the user has TWO bookings for the same trip!
```

### The Solution

The client sends a unique `idempotencyKey` with each booking request. The server guarantees: **same key = same result, no matter how many times you send it.**

```java
// BookingService.java — Three-step idempotency check
public BookingResponse initiateBooking(BookingRequest request, Long userId) {
    
    // Step 1: Check DB — was this key already processed?
    Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
        return toResponse(existing.get());  // Return the SAME booking!
    }
    
    // Step 2: Check Redis — is another request with this key in progress?
    if (idempotencyStore.isProcessing(request.idempotencyKey())) {
        throw new BusinessException("DUPLICATE_REQUEST", "Request is already being processed");
    }
    
    // Step 3: Mark as PROCESSING in Redis
    idempotencyStore.tryAcquire(request.idempotencyKey());
    
    // ... process booking ...
    
    // Step 4: Mark as COMPLETED in Redis
    idempotencyStore.markCompleted(request.idempotencyKey(), booking.getPnr());
}
```

### Two Layers of Protection

| Layer | Speed | Purpose |
|-------|-------|---------|
| **Redis** (IdempotencyStore) | ~0.1ms | Catch in-flight duplicates (user double-clicks within milliseconds) |
| **PostgreSQL** (unique constraint on `idempotency_key`) | ~2ms | Durable protection — catches anything Redis misses (e.g., Redis crash) |

### The State Machine

```
Request arrives with key "abc-123":

1. Key not found → Set "PROCESSING" in Redis (24hr TTL)
2. Process booking normally
3. On SUCCESS → Set "COMPLETED|PNR1234567" in Redis
4. On FAILURE → Delete key from Redis (allow retry)

Same key arrives again:
- DB has it → Return existing booking ✓
- Redis says "PROCESSING" → Return "already being processed" error
- Redis says "COMPLETED|PNR..." → (DB lookup returns the booking)
```

### Learn More
- [Stripe — Idempotency](https://stripe.com/docs/api/idempotent_requests)
- [Designing Idempotent APIs](https://blog.dreamfactory.com/what-is-idempotency/)

---

## 13. Optimistic Locking

### The Problem

Two threads read the same inventory row:

```
Thread A: SELECT available_seats FROM seat_inventory WHERE id=1 → 5
Thread B: SELECT available_seats FROM seat_inventory WHERE id=1 → 5
Thread A: UPDATE SET available_seats=4 WHERE id=1 → ✓ (5→4)
Thread B: UPDATE SET available_seats=4 WHERE id=1 → ✓ (5→4, but should be 3!)
```

One decrement is lost! This is called the **lost update** problem.

### The Solution: @Version

Add a version field. Include it in the WHERE clause of every UPDATE:

```java
@Entity
public class SeatInventory {
    @Version
    private Long version;  // Starts at 0, incremented on every update
}
```

```sql
-- SeatInventoryRepository.java
UPDATE seat_inventory
SET available_seats = available_seats - :count,
    version = version + 1          -- Bump version
WHERE id = :id
  AND available_seats >= :count
  AND version = :version           -- Only succeed if version hasn't changed!
```

**Now the scenario:**

```
Thread A: SELECT → version=0, seats=5
Thread B: SELECT → version=0, seats=5
Thread A: UPDATE WHERE version=0 → rows_updated=1 ✓ (version is now 1)
Thread B: UPDATE WHERE version=0 → rows_updated=0 ✗ (version is 1, not 0!)
```

Thread B's update fails (returns 0 rows updated). Our code detects this and throws a `CONCURRENT_BOOKING` error, telling the user to retry.

### Optimistic vs Pessimistic Locking

| | Optimistic | Pessimistic |
|--|-----------|-------------|
| **Approach** | Check at write time | Lock at read time |
| **SQL** | `WHERE version = ?` | `SELECT ... FOR UPDATE` |
| **Blocks others?** | No | Yes, until transaction commits |
| **Best for** | Low contention (most writes don't conflict) | High contention (many writes to same row) |
| **Our choice** | ✓ Used everywhere | Not used |

We chose optimistic locking because seat conflicts are relatively rare (most trains have many seats). When they do happen, retrying is cheap.

### Learn More
- [Vlad Mihalcea — Optimistic Locking](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/)
- [Baeldung — Optimistic Locking in JPA](https://www.baeldung.com/jpa-optimistic-locking)

---

## 14. DTO Pattern & Validation

### Why Not Return Entities Directly?

```java
// BAD: Returning entity exposes everything
@GetMapping("/users/me")
public User getMe() {
    return userRepository.findById(id);  // Exposes passwordHash! Security nightmare.
}

// GOOD: Return a DTO with only what the client needs
@GetMapping("/users/me")
public UserResponse getMe() {
    User user = userRepository.findById(id);
    return new UserResponse(user.getId(), user.getEmail(), user.getFullName());
}
```

**Reasons to use DTOs:**
1. **Security** — Don't expose passwords, internal IDs, or audit fields
2. **API stability** — Rename a DB column? The DTO stays the same, API doesn't break
3. **Validation** — Request DTOs carry validation annotations
4. **Different shapes** — The API response shape often differs from the DB entity shape

### Java Records as DTOs

```java
// BookingRequest.java — Immutable, compact, auto-generates equals/hashCode/toString
public record BookingRequest(
    @NotNull Long trainRunId,
    @NotBlank String coachType,
    @NotNull Long fromStationId,
    @NotNull Long toStationId,
    @NotEmpty @Size(max = 6) List<@Valid PassengerRequest> passengers,
    String idempotencyKey
) {
    public record PassengerRequest(
        @NotBlank String name,
        @Min(1) @Max(120) int age,
        @NotBlank String gender,
        String berthPreference
    ) {}
}
```

### Validation Annotations

| Annotation | What it checks |
|-----------|---------------|
| `@NotNull` | Field must not be null |
| `@NotBlank` | String must not be null, empty, or only whitespace |
| `@NotEmpty` | Collection must not be null or empty |
| `@Size(max=6)` | Collection/String size limit |
| `@Min(1) @Max(120)` | Numeric range |
| `@Valid` | Recursively validate nested objects |
| `@Email` | Valid email format |

**How it works:** `@Valid @RequestBody BookingRequest request` in the controller triggers automatic validation. If any field fails, Spring throws `MethodArgumentNotValidException` → our `GlobalExceptionHandler` returns a 400 error with field-specific messages.

### Learn More
- [Baeldung — Validation in Spring Boot](https://www.baeldung.com/spring-boot-bean-validation)
- [Java Records](https://docs.oracle.com/en/java/javase/17/language/records.html)

---

## 15. Exception Handling

### The Problem

Without a global handler, every controller needs try-catch blocks, and error responses are inconsistent:

```json
// Controller A returns: { "error": "Not found" }
// Controller B returns: { "message": "Resource missing", "code": 404 }
// Controller C throws a 500 with a stack trace
```

### The Solution: @RestControllerAdvice

One class handles ALL exceptions across ALL controllers:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(
            new ErrorResponse(ex.getErrorCode(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(400).body(
            new ErrorResponse(ex.getErrorCode(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)  // Catch-all safety net
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);  // Log full stack trace
        return ResponseEntity.status(500).body(
            new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", Instant.now()));
        // Never expose stack traces to clients!
    }
}
```

**Every API error returns the same shape:**

```json
{
    "code": "SEATS_UNAVAILABLE",
    "message": "Not enough seats available for the requested segment",
    "timestamp": "2026-04-21T10:30:00Z"
}
```

### Exception Hierarchy

```
BusinessException (base, 400)
├── ResourceNotFoundException (404)
└── DuplicateResourceException (409)
```

Custom error codes like `"RATE_LIMIT_EXCEEDED"`, `"CONCURRENT_BOOKING"`, `"DUPLICATE_REQUEST"` help the frontend show appropriate messages.

### Learn More
- [Baeldung — Error Handling for REST](https://www.baeldung.com/exception-handling-for-rest-with-spring)
- [RFC 7807 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807)

---

## 16. Docker & Containerization

### What is Docker?

Docker packages an application with ALL its dependencies into a **container** — a lightweight, isolated environment. "It works on my machine" becomes "it works in the container."

### Docker Compose

We use Docker Compose to run multiple containers together:

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine      # Official PostgreSQL image, Alpine = smaller
    environment:
      POSTGRES_DB: railway_booking
      POSTGRES_USER: railway
      POSTGRES_PASSWORD: railway_pass
    ports:
      - "5432:5432"                # host:container port mapping
    volumes:
      - postgres_data:/var/lib/postgresql/data  # Data survives container restarts
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U railway"]  # Is DB ready?

  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

volumes:
  postgres_data:    # Named volumes persist data between container restarts
  redis_data:
```

### Key Concepts

| Concept | What it means |
|---------|--------------|
| **Image** | A template (read-only). Like a class in OOP. |
| **Container** | A running instance of an image. Like an object. |
| **Volume** | Persistent storage. Containers are ephemeral — volumes survive restarts. |
| **Port mapping** | `5432:5432` = "host port 5432 → container port 5432" |
| **Healthcheck** | Docker periodically checks if the service is healthy |
| **Alpine** | Minimal Linux distro. `postgres:16-alpine` is ~80MB vs `postgres:16` at ~400MB |

### Commands You'll Use

```bash
docker compose up -d          # Start all services in background
docker compose down           # Stop all services
docker compose ps             # List running services
docker compose logs redis     # View Redis logs
docker exec -it railway-redis redis-cli  # Connect to Redis CLI
docker exec -it railway-postgres psql -U railway railway_booking  # Connect to PostgreSQL
```

### Learn More
- [Docker — Getting Started](https://docs.docker.com/get-started/)
- [Docker Compose Tutorial](https://docs.docker.com/compose/gettingstarted/)

---

## 17. API Design Best Practices

### RESTful URL Design

```
GET    /api/v1/trains              → List all trains
GET    /api/v1/trains/12301        → Get train by number
POST   /api/v1/admin/trains        → Create a train (admin)

GET    /api/v1/bookings/my         → My bookings (paginated)
GET    /api/v1/bookings/{pnr}      → Get booking by PNR
POST   /api/v1/bookings            → Create a booking

GET    /api/v1/availability        → Check seat availability
GET    /api/v1/pnr/{pnr}           → PNR status
```

**Principles:**
- **Nouns, not verbs** — `/bookings` not `/createBooking`
- **Plural nouns** — `/trains` not `/train`
- **Versioning** — `/api/v1/` allows breaking changes in `/api/v2/`
- **Admin prefix** — `/api/v1/admin/*` clearly separates admin operations
- **HTTP methods convey intent** — GET=read, POST=create, PUT=update, DELETE=delete

### Response Codes

| Code | When we use it |
|------|---------------|
| 200 | Successful GET |
| 201 | Successful POST (resource created) |
| 400 | Bad request (validation failed, business rule violated) |
| 401 | Not authenticated (no/invalid JWT) |
| 403 | Not authorized (valid JWT but wrong role) |
| 404 | Resource not found |
| 409 | Conflict (duplicate resource) |
| 429 | Rate limited |
| 500 | Server error (unexpected) |

### Pagination

```java
// Controller
@GetMapping("/my")
public ResponseEntity<PagedResponse<BookingResponse>> getMyBookings(
        @PageableDefault(size = 10) Pageable pageable) { ... }

// Response shape (PagedResponse record):
{
    "content": [...],
    "page": 0,
    "size": 10,
    "totalElements": 47,
    "totalPages": 5
}
```

Never return unbounded lists. Always paginate.

### Learn More
- [REST API Design Best Practices](https://restfulapi.net/)
- [Microsoft — API Design Guidelines](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)

---

## 18. Concurrency Control — The Full Picture

Our system handles concurrent booking requests at multiple levels:

```
Level 1: Rate Limiting (Redis Sorted Set)
    → Limits requests per user per minute
    → Prevents system overload

Level 2: Idempotency (Redis + DB)
    → Deduplicates identical requests
    → Prevents double bookings

Level 3: Distributed Lock (Redis Lua Script)
    → Atomically checks + decrements availability
    → 10-minute TTL for payment window

Level 4: Optimistic Lock (@Version in PostgreSQL)
    → Catches concurrent DB updates
    → Safety net if Redis lock fails

Level 5: Unique Constraints (PostgreSQL)
    → Prevents duplicate PNRs, duplicate idempotency keys
    → Last line of defense at the data layer
```

**Each level handles a different failure mode:**
- Rate limiting → abusive clients
- Idempotency → network retries
- Redis lock → concurrent seat selection
- @Version → concurrent DB writes
- Unique constraints → any bug in the above layers

**Defense in depth** — No single layer is 100% reliable. Together, they cover each other's failure modes.

---

## 19. Java Records & Modern Java

### Records (Java 16+)

Records are immutable data carriers. Perfect for DTOs:

```java
// This one line gives you: constructor, getters, equals, hashCode, toString
public record BookingResponse(
    Long id,
    String pnr,
    String bookingStatus,
    BigDecimal totalFare,
    List<PassengerResponse> passengers,
    Instant createdAt
) {}
```

Equivalent to ~80 lines of traditional Java with getters, constructor, equals, hashCode, toString.

### Pattern Matching (Java 17+)

```java
// Instead of:
if (handler instanceof HandlerMethod) {
    HandlerMethod method = (HandlerMethod) handler;
    // use method
}

// We write:
if (handler instanceof HandlerMethod method) {
    // method is already cast and available
}
```

### Switch Expressions (Java 17+)

```java
// BookingService.java — Fare calculation
BigDecimal baseRate = switch (coachType) {
    case "FIRST_AC"  -> new BigDecimal("2500");
    case "SECOND_AC" -> new BigDecimal("1500");
    case "THIRD_AC"  -> new BigDecimal("1000");
    case "SLEEPER"   -> new BigDecimal("500");
    default          -> new BigDecimal("200");
};
```

### Sealed Classes, Text Blocks, Stream API

These are all available in Java 17. You'll see `Stream` used extensively:

```java
List<Long> stationIds = routeStations.stream()
    .map(r -> ((Number) r[0]).longValue())
    .toList();  // Java 16+ — shorter than .collect(Collectors.toList())
```

### Learn More
- [Java 17 Features](https://openjdk.org/projects/jdk/17/)
- [Baeldung — Java Records](https://www.baeldung.com/java-record-keyword)

---

## 20. Lombok

Lombok generates boilerplate code at compile time via annotation processing.

| Annotation | What it generates |
|-----------|------------------|
| `@Getter / @Setter` | Getter and setter methods |
| `@RequiredArgsConstructor` | Constructor with all `final` fields |
| `@AllArgsConstructor` | Constructor with ALL fields |
| `@NoArgsConstructor` | Empty constructor |
| `@Builder` | Builder pattern (`Booking.builder().pnr("PNR123").build()`) |
| `@Data` | `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor` |
| `@Slf4j` | Creates `private static final Logger log = LoggerFactory.getLogger(...)` |

**Example from our code:**

```java
@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {
    // No getters/setters/constructors written — Lombok generates them all
}
```

**`@Builder` in action:**

```java
Booking booking = Booking.builder()
    .pnr(pnr)
    .userId(userId)
    .trainRunId(request.trainRunId())
    .bookingStatus(BookingStatus.PAYMENT_PENDING)
    .totalFare(fare)
    .build();
```

### Caution with Lombok
- `@Data` on JPA entities can cause issues (circular `toString()` with bidirectional relationships). We use `@Getter @Setter` instead.
- Lombok is a compile-time dependency. Your IDE needs the Lombok plugin to understand the generated code.

### Learn More
- [Project Lombok](https://projectlombok.org/features/)

---

## 21. Apache Kafka & Event-Driven Architecture

### What is Kafka?

Apache Kafka is a distributed event streaming platform. Think of it as a highly durable, high-throughput message queue where:
- **Producers** write messages (events) to **topics**
- **Consumers** read messages from topics
- Messages are **persisted to disk** (not lost after reading)
- Multiple consumers can independently read the same messages

### Why Event-Driven?

**Without events (direct calls):**
```java
// BookingService.java — tightly coupled
public void initiateBooking(...) {
    booking = save(booking);
    paymentService.process(booking);      // What if payment service is down?
    notificationService.send(booking);    // What if notification fails?
    searchIndexer.update(booking);        // Booking fails because indexer is broken?
}
```

**With events (decoupled):**
```java
// BookingService.java — just publishes an event
public void initiateBooking(...) {
    booking = save(booking);
    kafkaTemplate.send("booking.events", bookingEvent);  // Fire and forget
    // Payment, notification, search — all independent consumers
}
```

**Benefits:**
1. **Decoupling** — BookingService doesn't import PaymentService. Add a new consumer (analytics, fraud detection) without touching booking code
2. **Resilience** — If payment service is down, events queue up in Kafka. When it comes back, it processes the backlog
3. **Scalability** — Add more consumer instances to handle higher load. Kafka distributes partitions across them
4. **Auditability** — Every event is persisted. You have a complete history of everything that happened

### Key Kafka Concepts

| Concept | Explanation |
|---------|------------|
| **Topic** | A named channel for events. Like a database table for events. |
| **Partition** | A topic is split into partitions for parallelism. Ordering is guaranteed within a partition. |
| **Partition Key** | Determines which partition a message goes to. Same key = same partition = guaranteed order. |
| **Consumer Group** | Multiple consumers sharing the work. Each partition is read by only one consumer in the group. |
| **Offset** | A position in a partition. Each consumer tracks its offset (how far it's read). |
| **Broker** | A Kafka server. In production, you run 3+ brokers for fault tolerance. |

### How We Use Kafka

```
Our Topics:
  booking.events    (key: trainRunId)   →  Booking lifecycle events
  payment.events    (key: bookingId)    →  Payment result events
  *.retry           (auto-created)      →  Failed messages for retry
  *.dlt             (dead letter)       →  Messages that failed after all retries
```

**Producer example (our code):**
```java
// BookingEventPublisher.java
EventEnvelope<BookingEvent> envelope = EventEnvelope.<BookingEvent>builder()
    .eventType("BOOKING_INITIATED")
    .aggregateId(String.valueOf(booking.getId()))
    .source("railway-booking")
    .payload(bookingEvent)
    .build();

kafkaTemplate.send("booking.events",
    String.valueOf(booking.getTrainRunId()),  // Partition key
    envelope);
```

**Consumer example (our code):**
```java
// PaymentEventConsumer.java
@KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "booking-service")
@Transactional
public void handlePaymentEvent(EventEnvelope<?> envelope) {
    PaymentEvent event = objectMapper.convertValue(envelope.getPayload(), PaymentEvent.class);
    switch (envelope.getEventType()) {
        case "PAYMENT_SUCCESS" -> handlePaymentSuccess(event);
        case "PAYMENT_FAILED"  -> handlePaymentFailed(event);
    }
}
```

### Serialization — How Objects Become Kafka Messages

```yaml
# application.yml
spring.kafka.producer:
  value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer:
  value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
  properties:
    spring.json.trusted.packages: "com.railway.*"
```

Producer serializes Java objects → JSON bytes. Consumer deserializes JSON bytes → Java objects. The `trusted.packages` setting is a security measure — Kafka won't deserialize arbitrary classes (prevents deserialization attacks).

### Dead Letter Topics (DLT) — Handling Poison Pills

What happens when a consumer can't process a message?

```java
@RetryableTopic(
    attempts = "3",                              // Try 3 times total
    backoff = @Backoff(delay = 1000, multiplier = 2)  // 1s, 2s, 4s
)
@KafkaListener(topics = "payment.events")
public void handlePaymentEvent(EventEnvelope<?> envelope) {
    // If this throws an exception:
    // 1st attempt: immediate
    // 2nd attempt: after 1 second
    // 3rd attempt: after 2 seconds
    // After 3rd failure → message goes to payment.events.dlt
}

@DltHandler
public void handleDlt(EventEnvelope<?> envelope) {
    log.error("Dead letter: {}", envelope.getEventType());
    // Alert ops team, save to a DB table, etc.
}
```

**Why not infinite retries?** A malformed message will fail forever, blocking all other messages in the partition. Better to quarantine it in the DLT and process the rest.

### Consumer Idempotency

Kafka guarantees **at-least-once delivery** — a message might be delivered more than once (network retry, consumer restart). Your consumer must handle duplicates:

```java
private void handlePaymentSuccess(PaymentEvent event) {
    Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();

    // IDEMPOTENCY CHECK — if already confirmed, skip
    if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
        log.info("Already confirmed, skipping duplicate");
        return;
    }

    booking.setBookingStatus(BookingStatus.CONFIRMED);
    bookingRepository.save(booking);
}
```

### Topic Creation via Spring Beans

```java
// KafkaConfig.java
@Bean
public NewTopic bookingEventsTopic() {
    return TopicBuilder.name("booking.events")
        .partitions(3)    // 3 partitions for parallelism
        .replicas(1)      // 1 replica (dev only; production needs 3)
        .build();
}
```

Spring Kafka auto-creates these topics on application startup. In production, you'd create topics via infrastructure tooling with proper replication.

### Learn More
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent — Kafka 101](https://developer.confluent.io/learn-kafka/)
- [Designing Data-Intensive Applications — Ch. 11](https://dataintensive.net/) (Stream Processing)
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [Kafka: The Definitive Guide (book)](https://www.confluent.io/resources/kafka-the-definitive-guide-v2/)

---

## 22. Payment Processing Patterns

### Mock Gateway Pattern

In real systems, you integrate with payment gateways (Razorpay, Stripe, PayPal). During development, we use a mock:

```java
// MockPaymentGateway.java
public GatewayResponse processPayment(Long bookingId, BigDecimal amount, String method) {
    Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));  // Simulate latency

    boolean success = ThreadLocalRandom.current().nextInt(100) < 90;  // 90% success rate
    String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12);

    return new GatewayResponse(success, transactionId, message, failureReason);
}
```

**Why mock?** You don't want to hit a real payment gateway during development. The mock simulates real-world conditions: variable latency, occasional failures, and unique transaction IDs.

**Why 90% success?** Real payment gateways fail 5-15% of the time (expired cards, insufficient funds, network issues). Testing only the happy path hides bugs in your error handling.

### The Payment State Machine

```
INITIATED → PROCESSING → SUCCESS  (happy path)
                      → FAILED   (gateway rejected)
                                 → RETRY → PROCESSING → SUCCESS/FAILED
SUCCESS → REFUNDED  (on cancellation — Phase 5)
```

Each state transition is persisted to the database AND published as a Kafka event. This means:
1. The payment record in PostgreSQL is the source of truth
2. The Kafka event triggers downstream actions (booking confirmation, notifications)
3. If Kafka is down, the payment record still reflects the correct state

### Cross-Module Communication Without Direct Dependencies

The payment module needs to know the booking's PNR and fare. But it can't import `Booking.java` (wrong module). Solution: native SQL query.

```java
// PaymentService.java — reads from bookings table directly
private Object[] lookupBooking(Long bookingId) {
    return entityManager.createNativeQuery(
        "SELECT b.pnr, b.total_fare, b.booking_status FROM bookings b WHERE b.id = :id"
    ).setParameter("id", bookingId).getResultList().get(0);
}
```

This respects the modular monolith boundary: modules share a database but don't share Java classes. When you extract to microservices, this query becomes an API call.

### Payment Retry

Failed payments can be retried without creating a duplicate:

```
POST /api/v1/payments/{paymentId}/retry

→ Check: payment must be in FAILED status
→ Create a new Payment record (not modify the old one — audit trail!)
→ Call gateway again
→ Publish new event
```

### Learn More
- [Stripe — Building Robust Payment Systems](https://stripe.com/docs/payments)
- [Idempotent Payment Processing](https://stripe.com/docs/api/idempotent_requests)
- [Martin Fowler — Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)

---

## 23. Real Bugs We Hit & Fixed (Phase 1-3)

These are actual bugs encountered during development. Each one teaches a distributed systems or JPA lesson you won't find in tutorials.

### Bug 1: Hibernate Schema Validation — Type Mismatch

**Symptom:** App failed to start with `Schema-validation: wrong column type encountered in column [latitude]`

**Root cause:** The Flyway migration used `DECIMAL(10,7)` for latitude/longitude, but the Java entity used `Double`. Hibernate 6.4 maps `Double` to `float(53)` (PostgreSQL `double precision`), not `numeric`.

**Fix:** Changed migration to `DOUBLE PRECISION` to match what Hibernate expects for Java `Double`.

**Lesson:** `ddl-auto: validate` catches mismatches between your entities and your migrations. Always check that your SQL types map correctly to your Java types. Hibernate's type mapping changed between versions — what worked in Hibernate 5 may break in 6.

### Bug 2: Native Query Single-Column Cast — ClassCastException

**Symptom:** `POST /admin/train-runs/generate` returned 500.

**Root cause:** The native query `SELECT rs.station_id FROM route_stations` returns a **single column**. JPA returns each row as a plain `Number` object, NOT an `Object[]`. But the code cast it as `((Number) r[0]).longValue()` — treating `r` as an array when it's a scalar.

**Fix:** Changed `List<Object[]>` to `List<Number>` and `.map(Number::longValue)`.

**Lesson:** JPA native queries return `Object[]` only when selecting **multiple columns**. Single-column results come back as the raw type. This is a common trap when refactoring queries.

### Bug 3: LazyInitializationException — Accessing Relations Outside Transaction

**Symptom:** `GET /bookings/{pnr}` returned 500 with `LazyInitializationException: could not initialize proxy - no Session`.

**Root cause:** The `Booking` entity has `@OneToMany passengers` with default `FetchType.LAZY`. The `getBookingByPnr()` service method wasn't `@Transactional`. When `toResponse()` called `booking.getPassengers().stream()`, the Hibernate session was already closed.

**Why `@Transactional(readOnly = true)` didn't fix it:** The `@Transactional` annotation was added but still failed — because the `findByPnr` repository method ran in its own transaction (Spring Data auto-transaction), returned a detached entity, and the service-level transaction proxy wasn't properly wrapping the call.

**Fix:** Changed `@OneToMany` to `fetch = FetchType.EAGER`. For a booking with max 6 passengers, eager loading is perfectly acceptable — no N+1 concern.

**Alternative fix:** Use `@Query("SELECT b FROM Booking b LEFT JOIN FETCH b.passengers WHERE b.pnr = :pnr")` in the repository to eagerly load in a single query.

**Lesson:** `open-in-view: false` (which we correctly set) means you MUST load all data within a transaction. Lazy loading silently works with `open-in-view: true` but breaks in production under load. This bug is the #1 JPA gotcha for beginners. Options:
1. `FetchType.EAGER` — simple, fine for small collections (≤6 items)
2. `JOIN FETCH` in JPQL — explicit, best for large/optional collections
3. `@EntityGraph` — declarative, good for multiple associations
4. `@Transactional` on the service method — keeps session open, but risks N+1

### Bug 4: Kafka Listener Dual-Listener Configuration

**Symptom:** Kafka UI showed cluster as "Offline" even though Kafka was running and the Spring Boot app could produce/consume messages.

**Root cause:** Kafka was configured with a single advertised listener: `PLAINTEXT://localhost:9092`. The Spring Boot app (running on the host) could reach `localhost:9092`. But Kafka UI (running inside Docker) couldn't — `localhost` inside a container refers to the container itself, not the host.

**Fix:** Configured two listeners:
```yaml
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092
KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
```
- `INTERNAL://kafka:29092` — for Docker-to-Docker traffic (Kafka UI, Zookeeper)
- `EXTERNAL://localhost:9092` — for host-to-Docker traffic (Spring Boot app)

Kafka UI updated to use `kafka:29092` as bootstrap server.

**Lesson:** Kafka's `ADVERTISED_LISTENERS` is one of the most common Kafka configuration pitfalls. The advertised address is what Kafka tells clients to connect to — it must be reachable FROM the client. When clients are in different network contexts (host vs Docker), you need multiple listeners.

### Bug 5: Passenger Status Not Updating on Payment

**Symptom:** After successful payment, `bookingStatus` showed `CONFIRMED` but each passenger's `status` still showed `PAYMENT_PENDING`.

**Root cause:** The `PaymentEventConsumer.handlePaymentSuccess()` method updated `booking.setBookingStatus(CONFIRMED)` but never iterated through passengers to update their individual statuses.

**Fix:** Added `booking.getPassengers().forEach(p -> p.setStatus(BookingStatus.CONFIRMED))` before saving. Same fix for the failure path.

**Lesson:** When a parent entity's state changes, think about whether child entities need to follow. In a booking system, the passenger status is user-facing — it must stay in sync with the booking status. This is a domain modeling concern, not a technical one.

### Bug 6: Stale Compiled JARs in Multi-Module Build

**Symptom:** Code changes (like the passenger status fix above) appeared to have no effect after recompile.

**Root cause:** `mvn compile` compiles source files but may use cached JARs from the local Maven repository for inter-module dependencies. The `railway-app` module depends on `railway-booking` JAR — if the JAR wasn't reinstalled, the old code runs.

**Fix:** Use `mvn clean spring-boot:run -pl railway-app` — the `clean` phase deletes all compiled artifacts, forcing a full rebuild.

**Lesson:** In multi-module Maven projects, always use `mvn clean` when debugging issues where code changes don't take effect. For faster iteration, use `mvn clean compile && mvn spring-boot:run -pl railway-app`.

### Bug 7: Swallowed Exceptions in GlobalExceptionHandler

**Symptom:** API returned `{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}` with no stack trace in the console.

**Root cause:** The catch-all exception handler returned a generic 500 without logging the actual exception:
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
}
```

**Fix:** Added `log.error("Unhandled exception", ex)` to log the full stack trace before returning the generic response.

**Lesson:** NEVER swallow exceptions in a catch-all handler. The generic response to the client is correct (don't expose internals), but you MUST log the full exception server-side. Without it, debugging production issues is impossible.

---

## 24. What's Coming — Elasticsearch, CQRS, Waitlist

### Phase 4: Elasticsearch (CQRS)

**CQRS** = Command Query Responsibility Segregation.

```
WRITES go to PostgreSQL (the source of truth)
    ↓ Kafka event
READS come from Elasticsearch (optimized for search)
```

**Why**: PostgreSQL is great for transactions but slow for full-text search across millions of trains. Elasticsearch is purpose-built for search — fuzzy matching, autocomplete, relevance scoring.

### Phase 5: Waitlist/RAC + Notifications

Event-driven choreography: one cancellation triggers a chain of events — refund processing, waitlist promotion, notification sending — all asynchronous, all decoupled.

### Phase 6: Production Hardening

Testcontainers (integration tests with real Docker containers), circuit breakers (gracefully handle downstream failures), structured logging, metrics.

---

## 25. Learning Resources

### Books (Highly Recommended)

| Book | Why Read It |
|------|-------------|
| **Designing Data-Intensive Applications** by Martin Kleppmann | THE book on distributed systems. Covers everything we're building: replication, partitioning, consistency, batch/stream processing. |
| **System Design Interview Vol 1 & 2** by Alex Xu | Practical system design problems. Rate limiters, key-value stores, notification systems — directly relevant. |
| **Spring in Action** by Craig Walls | Deep dive into Spring Boot/Framework internals. |
| **Java Concurrency in Practice** by Brian Goetz | If you want to truly understand race conditions, atomicity, and thread safety. |
| **Redis in Action** by Josiah Carlson | Redis patterns beyond basic caching. |

### Online Courses

| Course | Platform |
|--------|----------|
| Redis University courses | [university.redis.io](https://university.redis.io/) (free) |
| Apache Kafka for Beginners | Udemy — Stephane Maarek |
| Spring Boot Master Class | Udemy — in28Minutes |

### Websites & Blogs

| Resource | What You'll Learn |
|----------|------------------|
| [Baeldung](https://www.baeldung.com/) | Spring/Java tutorials — the best reference for Spring-related how-tos |
| [Vlad Mihalcea's Blog](https://vladmihalcea.com/) | JPA/Hibernate performance — the deepest resource on JPA |
| [Martin Fowler's Blog](https://martinfowler.com/) | Architecture patterns — CQRS, Event Sourcing, Microservices |
| [ByteByteGo](https://bytebytego.com/) | System design — visual explanations of distributed systems |
| [Use The Index, Luke](https://use-the-index-luke.com/) | SQL indexing — free online book, essential for database performance |
| [The Morning Paper](https://blog.acolyer.org/) | Distributed systems research papers explained simply |
| [High Scalability](http://highscalability.com/) | Real-world architecture case studies |

### Documentation

| Tech | Link |
|------|------|
| Spring Boot 3.2 | [docs.spring.io/spring-boot](https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/) |
| Spring Security | [docs.spring.io/spring-security](https://docs.spring.io/spring-security/reference/) |
| Spring Data JPA | [docs.spring.io/spring-data/jpa](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/) |
| PostgreSQL 16 | [postgresql.org/docs/16](https://www.postgresql.org/docs/16/) |
| Redis | [redis.io/docs](https://redis.io/docs/) |
| Apache Kafka | [kafka.apache.org/documentation](https://kafka.apache.org/documentation/) |
| Elasticsearch | [elastic.co/guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html) |
| Flyway | [documentation.red-gate.com/flyway](https://documentation.red-gate.com/flyway) |
| Docker | [docs.docker.com](https://docs.docker.com/) |

### Practice

| What | Where |
|------|-------|
| System Design practice | [interviewing.io](https://interviewing.io/), [pramp.com](https://www.pramp.com/) |
| SQL exercises | [pgexercises.com](https://pgexercises.com/) (PostgreSQL specific) |
| Redis exercises | [try.redis.io](https://try.redis.io/) (interactive Redis tutorial) |

---

> **Tip**: Don't try to learn everything at once. Each phase of this project introduces 2-3 new concepts.
> Build it, break it, debug it — that's how you actually learn distributed systems.
