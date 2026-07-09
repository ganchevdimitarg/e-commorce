# CLAUDE.md

> **Repo.** `com.ganchevdimitarg:e-commerce` — a Spring Boot 4.1.0 / Java 25 multi-module
> monorepo: `gateway` (WebFlux edge) + business services (`authentication`, `catalog`,
> `profile`, `order`, `payment`, `notification`) on PostgreSQL/MongoDB, plus
> `eureka-server`, `config-server`, `client`. Conventions below are the **target**; all
> modules are on Boot 4.1.0 as of 2026-07-08 (notification's full remediation is landing
> from `feat/notification-boot4-migration`) — migrate toward these when you touch
> lagging code, never copy a legacy pattern forward. Section-level "aspirational" notes
> mark not-yet-built infrastructure (`common-events`, Schema Registry, cross-service
> sagas). The root reactor builds green; a single module can still be built standalone
> with `./mvnw -f <module>/pom.xml ...`.
>
> Each module has its own thin `CLAUDE.md` (identity + deltas only). Read it alongside
> this file when working in that module.

---

## Context loading (progressive disclosure)

Core conventions here are always in context. **Detailed pattern files load on demand** —
read the file when the task touches that area. The `@import` lines below pull in
cross-cutting patterns; everything situational is in the table.

| When you work on… | Read on demand |
|---|---|
| Outbound HTTP / circuit breakers | `docs/context/resilience.md` |
| Mutating cross-service endpoints | `docs/context/idempotency.md` |
| Redis / caching | `docs/context/caching.md` |
| Kafka producers / consumers | `docs/context/kafka.md` · `.claude/context/kafka-setup.md` |
| Avro schemas / Schema Registry | `docs/context/avro-patterns.md` |
| MongoDB documents / queries | `docs/context/mongodb-patterns.md` |
| Dockerfiles | `docs/context/docker-patterns.md` |
| Observability / tracing / MDC | `docs/context/observability.md` |
| Choreography sagas | `docs/context/sagas.md` |
| Feature flags | `docs/context/feature-flags.md` |
| Read replicas (catalog) | `docs/context/read-replica-patterns.md` |
| CI/CD pipeline (catalog) | `docs/context/cicd-patterns.md` |
| Incident commands | `docs/context/runbook.md` |

## Stack
- Java 25 · virtual threads default · `ScopedValue` over `ThreadLocal` · `SequencedCollection` · records preferred over classes for data carriers
- Spring Boot 4.1.0 · WebMVC for business services · WebFlux for `gateway` · no XML config · problem+json errors (RFC 9457)
- PostgreSQL · Flyway migrations · JSONB only for schemaless data · typed columns preferred
- MongoDB · `profile` only · aggregation pipeline over app-side joins
- Redis · Lettuce · JSON serialization (Jackson) · keyspace `<service>:<entity>:<id>` · TTL always set
- Kafka · topic `<domain>.<entity>.<event>` · consumer group `<service>-group` · DLT `<topic>.DLT` (Avro/Schema-Registry aspirational; JSON today)
- Docker · multi-stage · non-root user · HEALTHCHECK mandatory · explicit artifact name in COPY · Flyway owns all schema changes (versioned migrations)

## Architecture
- Each service owns its schema — no cross-DB joins, no shared datasources
- Sync REST/WebMVC for reads (WebFlux in `gateway`); async Kafka events for cross-service writes
- `gateway` owns auth/rate-limit; downstream services trust `X-User-Id` / `X-User-Roles` headers
- Resilience4j circuit breaker + bulkhead on every outbound HTTP call; API versioning `/api/v{n}/` (maintain n-1)

---

## Java 25 & Spring Boot 4 Conventions

### New platform features — use these
- `ScopedValue` for request-scoped context — never `ThreadLocal` on virtual threads; `StructuredTaskScope` for parallel fan-out
- `SequencedCollection` / `SequencedMap` for ordered access; sealed interfaces + exhaustive `switch` — no `instanceof` chains
- Records **preferred** for DTOs, commands, query results, API request/response, event payloads, value objects (immutable, no JPA concern)
- `String.format()` or text blocks for multiline strings — no concatenation in hot paths

@docs/context/java25-patterns.md

### Lombok
- `@Value` + `@Builder` on immutable DTOs/commands/events — only when a record won't do (custom Jackson deser / inheritance)
- JPA entities: `@Getter` + `@Setter` + `@NoArgsConstructor` only (`@Builder` allowed); never `@Data`/`@ToString`/`@EqualsAndHashCode` with associations
- `@RequiredArgsConstructor` on `@Service`/`@Component` (never `@Autowired`); `@Slf4j` for logging (never a manual `Logger`)

@docs/context/lombok-records-patterns.md

### General
- `@Transactional` on the service layer only — never controllers or repository interfaces
- Repositories return `Optional<T>` — never null; service unwraps via `orElseThrow()`
- Never `Optional.get()` without a guard — `orElseThrow(() -> new NotFoundException(...))`
- Domain exceptions extend `BusinessException(HttpStatus, String code, String message)`
- No raw `500` responses — all errors via `@ControllerAdvice` producing `application/problem+json`

### Spring Security
- Stateless JWT validation at `gateway`; downstream services read `X-User-Id` / `X-User-Roles` headers
- Each service declares a `SecurityFilterChain` bean — never rely on auto-config defaults
- Method security (`@PreAuthorize`) on the service layer, not the controller

@docs/context/security.md

### Jackson
- Global config in a `JacksonConfig` `@Configuration` bean — never a per-controller `ObjectMapper`
- `camelCase` for REST; `NON_NULL` globally (no null fields in responses)
- Dates: ISO-8601 strings (`JsonFormat.Shape.STRING`) — never epoch longs
- `FAIL_ON_UNKNOWN_PROPERTIES = false` on deserialization (tolerant consumer)

### Pagination
- Wrap `Page<T>` in a `PageResponse<T>` record — never expose raw `Page<Entity>`
- Default page size 20, maximum 100 — enforce `@Max(100)` on the `size` param
- Accept `Pageable` via `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`

@docs/context/pagination-patterns.md

### Exception hierarchy
`BusinessException(HttpStatus, String code, String message)` is the base. Subclasses:
`NotFoundException` (404), `ConflictException` (409), `ValidationException` (400).
`@ControllerAdvice` maps all → problem+json. Never catch and rethrow as `RuntimeException`.
@docs/context/exceptions.md

### Input validation
- Bean Validation constraints (`@NotNull`, `@Size`, `@Pattern`) on record components — not service params
- `@Valid` on controller method parameters — validated before reaching the service
- Cross-field / business rules in the record's compact constructor, throwing `ValidationException`

@docs/context/validation-patterns.md

---

## Resilience4j & SLO defaults
Wrap every outbound HTTP call with `@CircuitBreaker` + `@Bulkhead` + `@TimeLimiter`.
Defaults (override per service in `application.yml`): failure rate 50%, slow call 2s, wait
open 30s, half-open 5 calls, bulkhead 10 concurrent, timeout 5s. Fallback method =
original signature + a trailing `Throwable`. → `docs/context/resilience.md`

## Idempotency
All mutating REST endpoints (POST/PUT/PATCH) crossing a service boundary or modifying
persistent state must support an `Idempotency-Key` header: check Redis
`idempotency:<service>:<key>`, return the cached response on hit; on miss process, store
with 24h TTL, return. Key is scoped to the service, never per-user. → `docs/context/idempotency.md`

## Caching strategy
Redis cache-aside by default (do **not** use Spring `@Cacheable` for new code — it hides
TTL/serialization; catalog's existing `@Cacheable` use is a documented exception). Cache
read-heavy rarely-mutated data and idempotency/correlation keys; never cache
strongly-consistent data (balances, inventory). Invalidate via TTL, write-through, or a
Kafka event. → `docs/context/caching.md`

## Redis
- Jackson JSON (`GenericJackson2JsonRedisSerializer`) — never Java serialization; key `<service>:<entity>:<id>`; TTL always set (default 24h)
- Distributed lock via Redisson `RLock` for idempotency guards — never manual `SETNX`

---

## Flyway
All schema changes are versioned migrations under `src/main/resources/db/migration/`.
Never set `ddl-auto` to anything but `validate`; never `spring.flyway.enabled=false` in any
profile.
- `V<n>__<snake_case>.sql` — two underscores, monotonically increasing; one logical change each (table ≠ index ≠ constraint)
- `R__<description>.sql` for seed/reference data only
- `IF [NOT] EXISTS` guards on DDL; never edit a committed migration — add a new version

@docs/context/database-patterns.md

## Database conventions
Every table includes `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`,
`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `deleted_at TIMESTAMPTZ NULL`.
Soft-delete: set `deleted_at = now()` — never `DELETE`. All queries filter
`WHERE deleted_at IS NULL`.

---

## Testing
- Unit: JUnit 5 + AssertJ — real objects for domain logic
- Integration: Testcontainers — extend `AbstractIntegrationTest`; never H2/EmbeddedMongo; never mock the DB/cache layer
- Naming: `should_<expectedBehavior>_when_<condition>`
- No `Thread.sleep()` — use Awaitility; never `spring.flyway.enabled=false` in test profiles
- Coverage gate: 80% line · 100% on domain model

@.claude/context/testcontainers-patterns.md

## CI/CD & Docker
PR: `test` + `build` + `checkstyle`. Image tag `<service>:<git-sha>` — never `:latest`.
Secrets never in code/Dockerfiles/`application.yml` — Vault / CI secrets → `${ENV_VAR}`.
Docker: multi-stage `eclipse-temurin:25-jdk` → `:25-jre`, non-root user, explicit artifact
name (no `*.jar` glob), HEALTHCHECK mandatory. catalog uses a Jenkins pipeline.
→ `docs/context/docker-patterns.md` · `docs/context/cicd-patterns.md`

---

## Verify — run after every change, do not stop until green
```bash
./mvnw -f <module>/pom.xml clean verify    # build + test (standalone; root reactor is blocked)
./mvnw -f <module>/pom.xml checkstyle:check # lint
./mvnw -f <module>/pom.xml flyway:validate  # migration drift
```
Fix root causes. Never suppress errors. Never skip tests to pass a build.

---

## Naming conventions
Package `com.ganchevdimitarg.<service>.<layer>`. `<Entity>{Service,Repository,Controller}`,
`<Entity>Response` (DTO), `<Reason>Exception`, `<Subject>Config`. Domain event
`<Entity><PastTense>Event` (`OrderPlacedEvent`); command `<Verb><Entity>Command`; Kafka
topic constant `<DOMAIN>_<ENTITY>_<EVENT>`.

## Project layout
Multi-module monorepo: `gateway` (WebFlux) + business services (PG) + `profile` (Mongo) +
`eureka-server`/`config-server`/`client`. Single canonical Claude config in root `.claude/`;
pattern docs in `docs/context/`. @docs/context/project-layout.md

---

## Never
- `@Data`/`@ToString`/`@EqualsAndHashCode` on JPA entities with associations; `@Data` on domain objects with logic
- `@SuppressWarnings` to hide compile/lint failures; `Optional.get()` without a guard (use `orElseThrow()`)
- `ddl-auto=create`/`update`, `spring.flyway.enabled=false`, or editing a committed migration — Flyway owns the schema
- Commit secrets/tokens/passwords; add a dependency without pinning its version in the root `pom.xml` BOM
- H2 / EmbeddedMongo in integration tests
- `@Transactional` on controllers or repositories; Bean Validation on service-layer params (belongs on records/controllers)
- `ThreadLocal` — use `ScopedValue`; reactive types (`Mono`/`Flux`/WebFlux) outside `gateway` (business services are WebMVC)
- `*.jar` glob in Dockerfile COPY; `git add -A` in automation — stage explicit paths
- Java serialization for Redis values (use Jackson JSON); an immortal Redis key (always set TTL)
- Classes instead of records for immutable DTOs/commands/responses/value objects
- Null fields in API responses (`NON_NULL`); raw `Page<Entity>` (map to `PageResponse`)
- Catch a `BusinessException` subclass and rethrow as `RuntimeException`
- Hard-delete business data — soft-delete via `deleted_at = now()`
- Avro field without `"default"`; remove/rename/retype an existing Avro field — add a new one
- POST/PUT/PATCH mutating state without `Idempotency-Key`; outbound HTTP without `@CircuitBreaker` + `@Bulkhead`

---

## Context management
`/compact` after a feature, `/clear` between unrelated tasks; `/effort high` for
architecture/debugging/migrations. After `/compact`, Claude reads
`.claude/session-checkpoint.md`. Read `docs/decisions.md` before architectural changes.
After resolving a non-obvious issue, append to `MEMORY.md` under `## Solved problems`:
`- [YYYY-MM] <module>: <what was wrong> → <what fixed it>`.

## Agents & skills
Slash commands: `/write`, `/review`, `/commit`, `/test`, `/migrate` (`.claude/skills/<name>/SKILL.md`).
Sub-agents: code-writer, code-reviewer, git-agent, test-agent, scaffold-agent, debug-agent,
performance-agent (`.claude/agents/*.md`).
