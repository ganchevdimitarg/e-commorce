# CLAUDE.md — catalog-service

> **What this module is.** `catalog-service` (package `com.concordeu.catalog`) is a
> **Spring Boot 4 WebMVC business service** in the `com.concordeu:e-commerce` Maven
> multi-module monorepo. It owns the product catalogue: **products, categories, comments**,
> backed by **PostgreSQL**. It is a stateless OAuth2 **resource server**, registered with
> **Eureka**, configured via **Spring Cloud Vault**, and traced via the **OpenTelemetry** (OTLP) bridge + Prometheus.
>
> This file describes the **target conventions** the parent pom already pins (Boot 4.1.0,
> Java 25). Parts of the catalog source still lag that target — see
> [Known migration gaps](#known-migration-gaps). When you touch lagging code, migrate it
> toward these conventions; do not copy the legacy pattern forward.

---

## The monorepo

Parent: `com.concordeu:e-commerce` (Spring Boot **4.1.0**, Java **25**). Modules:

| Module | Role | Port |
|---|---|---|
| `eureka-server` | Service discovery | 8761 |
| `config-server` | Centralised config | — |
| `gateway` | Edge: routing, auth, rate-limit (WebFlux) | 8080 |
| `authentication` | OAuth2 **authorization server** (issues/introspects tokens) | 8082 |
| **`catalog`** | **Product catalogue (this module, WebMVC)** | **8084** |
| `profile` · `order` · `payment` · `notification` | Other business services | — |
| `client` | Shared lib — e.g. `CustomOpaqueTokenIntrospector` | — |

Cross-cutting: Spring Cloud (Eureka, Vault, Bootstrap), Resilience4j, Micrometer/Prometheus,
OpenTelemetry (OTLP) tracing. As of **Phase 5**, catalog uses **Redis** (idempotency + read caching) and
**Kafka** (product domain events, JSON-over-Kafka mirroring `order`). **No MongoDB or Avro /
schema registry in catalog** — do not scaffold those here.

---

## Stack (catalog)

- **Java 25** · Spring Boot **4.1.0** · **WebMVC** (records preferred for DTOs/commands/responses)
- **PostgreSQL** (`catalog` DB) · Spring Data JPA · **Flyway** migrations · `ddl-auto: validate`
- **Redis** (Spring Data Redis + Spring Cache): `Idempotency-Key` guard (`catalog:idempotency:<key>`,
  24h TTL, duplicate → 409) and product read cache-aside (`catalog:product::<id>`, write-through evict)
- **Kafka** (spring-kafka, JSON via `JsonSerializer` — no schema registry): product domain events on
  `catalog.product.{created,updated,deleted}` (mirrors the `order` module's producer pattern)
- **Security**: OAuth2 resource server — validates JWT (issuer `:8082`) **and** opaque tokens
  (introspection via `client`'s `CustomOpaqueTokenIntrospector`); scopes `catalog.read` / `catalog.write`
- **Discovery / config**: Eureka client · Spring Cloud Vault (token auth, `bootstrap.yml`)
- **Resilience**: Resilience4j (circuitbreaker) on outbound calls
- **Mapping**: MapStruct (`MapStructMapper`) + `lombok-mapstruct-binding`
- **API docs**: springdoc-openapi + Swagger UI (OAuth2 PKCE flow)
- **Observability**: Micrometer → Prometheus · OpenTelemetry (OTLP) tracing + log export · actuator
- **Lombok** — annotation rules below
- **Build**: `./mvnw` (Maven wrapper) — never bare `mvn`

---

## Architecture

- catalog owns its `catalog` schema — no cross-DB joins, no shared datasources
- Reads/writes are synchronous REST (WebMVC). Cross-service calls go out via Eureka-resolved
  clients wrapped in Resilience4j
- The gateway terminates auth; catalog independently re-validates the bearer token as a
  resource server (defence in depth) and authorises on `SCOPE_catalog.*`
- API versioning: URL prefix `/api/v1/...` (e.g. `/api/v1/catalog/product/...`) — bump only on
  breaking changes
- Secrets come from Vault via `spring-cloud-vault-config`, surfaced as `${ENV_VAR}` — never inline

---

## Java 25 & Spring Boot 4 conventions

### Platform features
- **Virtual threads** for blocking I/O fan-out
- `record` **preferred** for DTOs, commands, query results, API request/response, value objects —
  anything immutable with no JPA concern (catalog already does this: `ProductResponseDto`, etc.)
- Record patterns + pattern-matching `switch` over `instanceof` chains
- `SequencedCollection` / `SequencedMap` where ordered access matters
- `StructuredTaskScope` / `ScopedValue` (stable in 25) for structured concurrency and request-scoped context
- `String.format()` / text blocks for multiline strings — no string concatenation in hot paths

@docs/context/java25-patterns.md

### Lombok
- `@Getter` + `@Setter` + `@NoArgsConstructor` on JPA entities — **nothing more**
- `@RequiredArgsConstructor` on `@Service` / `@Component` — never `@Autowired`
- `@Slf4j` for all logging — never declare a `Logger` manually
- Never `@Data`, `@ToString`, or `@EqualsAndHashCode` on JPA entities with associations (N+1 / recursion)
- `@Value` + `@Builder` only when a record genuinely won't do (custom Jackson deser / inheritance)

@docs/context/lombok-records-patterns.md

### General
- `@Transactional` on the service layer only — never controllers or repository interfaces
- Repositories return `Optional<T>` — service unwraps via `orElseThrow(...)`; never `Optional.get()` unguarded
- Domain failures use the `BusinessException` hierarchy (see below) — **not** raw `IllegalArgumentException`
- All errors mapped to `application/problem+json` (RFC 9457) via `@ControllerAdvice` — no raw 500s

### Spring Security
- Declare the `SecurityFilterChain` bean explicitly (`ResourceServerConfig`) — never rely on auto-config
- Resource server validates both JWT and opaque tokens via `AuthenticationManagerResolver`
- Authorise with `@PreAuthorize("hasAuthority('SCOPE_catalog.read'|'SCOPE_catalog.write')")`
- Public endpoints (swagger, actuator, public GET reads) listed explicitly in the filter chain

@docs/context/security.md

### Jackson
- Global config in a `@Configuration` bean — never per-controller `ObjectMapper`
- `camelCase`; `NON_NULL` globally (no null fields in responses); ISO-8601 date strings (never epoch longs)
- `FAIL_ON_UNKNOWN_PROPERTIES = false` (tolerant consumer)

### Pagination
- Wrap `Page<T>` in a `PageResponse<T>` record — never return a raw `Page<Entity>` or `Page<Dto>` to the API
- Accept `Pageable` via `@PageableDefault(size = 20)`; default size 20, max 100 (`@Max(100)`)
  (current product/category endpoints take raw `int page, int size` — migrate on touch)

@docs/context/pagination-patterns.md

### Exception hierarchy
`BusinessException(HttpStatus, String code, String message)` is the base.
Subclasses: `NotFoundException` (404), `ConflictException` (409), `ValidationException` (400).
`@ControllerAdvice` maps all → problem+json. Never catch a `BusinessException` and rethrow as `RuntimeException`.

@docs/context/exceptions.md

### Input validation
- Bean Validation constraints (`@NotEmpty`, `@Size`, `@Pattern`) on record components / entity fields
- `@Valid` on every `@RequestBody` controller param — validated before the service runs
- Cross-field / business rules in the record's compact constructor, throwing `ValidationException`
- Don't re-implement in a service-layer validator what a Bean Validation constraint already expresses

@docs/context/validation-patterns.md

---

## Resilience4j

Wrap outbound HTTP calls with `@CircuitBreaker` (+ `@Bulkhead`/`@TimeLimiter` where useful).
Defaults: failure rate 50%, slow-call 2s, wait-open 30s, half-open 5 calls, timeout 5s.
Fallback method = same signature + a trailing `Throwable`. Circuit-breaker health is exposed at
`/actuator/health` (`management.health.circuitbreakers.enabled=true`).

@docs/context/resilience.md

---

## Observability

- Tracing via the Micrometer OpenTelemetry bridge, exported over OTLP (`management.otlp.tracing.endpoint`,
  sampled per `management.tracing.sampling.probability`); propagate W3C `traceparent` on outbound HTTP
- MDC keys at request entry, cleared on exit: `traceId`, `spanId`, `userId`, `serviceId`
- Metrics via Micrometer → Prometheus; custom metric names `catalog.<entity>.<action>`
  (e.g. `catalog.product.created`)
- `/actuator/health` exposes details; lock down public exposure before prod
  (actuator exposure narrowed to `health, info, prometheus`)

**Kafka (product domain events):** JSON serialisation via `JsonSerializer` (no Avro/schema registry).
Topics: `catalog.product.{created,updated,deleted}`. Events published after commit via
`TransactionSynchronization.afterCommit()`. Producer: acks=all, idempotence enabled, observation
enabled. Sealed interface `ProductEvent` with record subtypes. Reference: `@docs/context/kafka-patterns.md`

**Redis:** Idempotency via `Idempotency-Key` header → Redis SETNX (24h TTL, duplicate → 409). Read
cache via `@Cacheable`/`@CacheEvict` (10-min TTL, namespace `catalog:`).
`GenericJackson2JsonRedisSerializer` — DTO renames invalidate cache. Reference: `@docs/context/caching.md`

**Read replica:** `DataSourceRouter` extends `AbstractRoutingDataSource`, routes on
`isCurrentTransactionReadOnly()`. Writer pool (10, :5432) / reader pool (20, :5433).
`LazyConnectionDataSourceProxy`. Health probe every 2s with `tryLock()`. Graceful fallback to writer.
Reference: `@docs/context/read-replica-patterns.md`

**CI/CD:** Jenkins declarative pipeline (`Jenkinsfile`). Stages: checkout → build → unit test
(`-Dgroups=unit`, Surefire) → verify (full suite incl. Testcontainers; `jacoco:check` gate) →
publish coverage → Docker build → push → deploy. PR branches skip deploy. Images tagged
`catalog:<git-sha>`. Reference: `@docs/context/cicd-patterns.md`

---

## Flyway

All schema changes are versioned migrations under `src/main/resources/db/migration/`.
Never set `ddl-auto` to anything but `validate`; never disable Flyway in any profile.

```
V1__init_tables.sql        ← tables
V2__create_indexes.sql     ← indexes (separate migration)
V3__create_constraints.sql ← constraints
```

Rules:
- `V<n>__<snake_case>.sql` — two underscores, monotonically increasing
- One logical change per migration (table ≠ index ≠ constraint)
- `IF [NOT] EXISTS` guards on DDL
- **Never edit a committed migration** — add a new version
- **New tables must include audit columns** (see below)

@docs/context/database-patterns.md

---

## Database conventions

### Audit columns — every new table must include
`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`,
`deleted_at TIMESTAMPTZ NULL`.
Soft-delete: set `deleted_at = now()` — never `DELETE`. All queries filter `WHERE deleted_at IS NULL`.

> All tables carry audit columns (added in `V4`). Soft-delete is active — set `deleted_at = now()`
> rather than issuing `DELETE`; all queries filter `WHERE deleted_at IS NULL`.

@docs/context/database-patterns.md

---

## Testing

- Unit: JUnit 5 + AssertJ; Mockito for collaborators, real objects for domain logic
- Naming: `should_<expectedBehavior>_when_<condition>`
- **Integration tests should use Testcontainers (PostgreSQL) + real Flyway** — see
  `docs/context/testcontainers-patterns.md`
- Coverage gate: 80% line · 100% on domain logic

> Tests use **Testcontainers** (PostgreSQL 16) + real Flyway migrations. No H2. Unit vs integration
> are split by JUnit 5 tags (`@Tag("unit")` / `@Tag("integration")`).

@docs/context/testcontainers-patterns.md

---

## Docker

Multi-stage build on `eclipse-temurin:25`. Non-root `USER`. Explicit artifact name in `COPY`
(no `*.jar` glob). `HEALTHCHECK` hitting `/actuator/health`.

@docs/context/docker-patterns.md

---

## Known migration gaps

The pom targets Boot 4.1.0 / Java 25, but parts of the catalog source still use the Boot 2.x
idiom. When you touch these, migrate them — do not propagate the old pattern:

| Gap | Status |
|---|---|
| Persistence/validation imports (`javax.*` → `jakarta.*`) | **Done** |
| Security DSL (lambda DSL, `requestMatchers()`, `@EnableMethodSecurity`) | **Done** |
| OpenAPI (`springdoc-openapi-starter-webmvc-ui`) | **Done** |
| Tests (Testcontainers + Flyway) | **Done** |
| Errors (`BusinessException` hierarchy, problem+json) | **Done** |
| Pagination (raw `int page, int size` → `PageResponse<T>`, `@PageableDefault`) | **Open** — migrate on touch |
| Package typo (`excaption` → `exception`) | **Done** |
| Repo naming (`*Dao` → `*Repository`) | **Done** |
| Java 25 | **Done** |

---

## Local development

Infrastructure (Postgres, Vault, Eureka, auth server, OTLP Collector) runs via the monorepo's root
`docker-compose`. catalog depends on: PostgreSQL `:5432` (`catalog` DB), Vault `:8200`,
Eureka `:8761`, auth server `:8082`, OTLP Collector `:4318`.

Catalog auto-manages its own data-plane infra (PostgreSQL writer/reader, Redis, Kafka) via
`catalog/compose.yaml` on `./mvnw spring-boot:run` (dev profile; skipped in tests, which use
Testcontainers); shared platform services (Vault, Eureka, auth server, OTLP collector) remain in
the monorepo root `docker-compose.yaml`.

```bash
./mvnw spring-boot:run -pl catalog            # run catalog (profile: dev, port 8084)
./mvnw clean verify -pl catalog -am           # build + test
./mvnw flyway:validate -pl catalog            # migration drift
```

Required env: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `VAULT_DEV_ROOT_TOKEN_ID`.

---

## Naming conventions

| Concept | Pattern | Example |
|---|---|---|
| Package | `com.concordeu.catalog.<layer>` | `com.concordeu.catalog.service.product` |
| Service | `<Entity>Service` / `<Entity>ServiceImpl` | `ProductService` |
| Repository | `<Entity>Repository` | `ProductRepository` |
| Controller | `<Entity>Controller` | `ProductController` |
| DTO / response | `<Entity>RequestDto` / `<Entity>ResponseDto` (records) | `ProductResponseDto` |
| Exception | `<Reason>Exception` | `ProductNotFoundException` |
| Config class | `<Subject>Config` | `ResourceServerConfig`, `OpenAPI3Config` |
| Custom metric | `catalog.<entity>.<action>` | `catalog.product.created` |

---

## Never

- `@Data` / `@ToString` / `@EqualsAndHashCode` on JPA entities with associations
- `@SuppressWarnings` to hide compile/lint failures
- `Optional.get()` without a guard — use `orElseThrow()`
- `ddl-auto` other than `validate`; `spring.flyway.enabled=false` in any profile
- Edit a committed Flyway migration — create a new version
- Commit secrets, tokens, or passwords (use Vault → `${ENV_VAR}`)
- `git add -A` in automation — stage explicit paths
- Classes instead of records for immutable DTOs/commands/responses/value objects
- Reactive types (`Mono`/`Flux`) in business logic — WebMVC here; reactive belongs in `gateway`
- Null fields in API responses — configure Jackson `NON_NULL` globally
- Raw `Page<Entity>` in API responses — map to `PageResponse<Record>`
- Catch a `BusinessException` and rethrow as `RuntimeException` — let `@ControllerAdvice` handle it
- Outbound HTTP calls without a Resilience4j circuit breaker
- Hard-delete business data — soft-delete via `deleted_at = now()`
- H2 in integration tests — use Testcontainers
- Add a dependency without pinning its version in the parent pom / module properties

---

## Agents & skills

Slash commands: `/write`, `/review`, `/commit`, `/test`, `/migrate`, `brainstorming`
(`.claude/skills/<name>/SKILL.md`).
Sub-agents: code-writer, code-reviewer, git-agent, test-agent, scaffold-agent, debug-agent,
performance-agent (`.claude/agents/*.md`).

---

## Context management

- `/compact` after finishing a feature, before the next one
- `/clear` between unrelated tasks
- Read `docs/decisions.md` before proposing architectural changes
- After resolving a non-obvious issue, append a one-liner to `MEMORY.md` under `## Solved problems`:
  `- [YYYY-MM] catalog: <what was wrong> → <what fixed it>`

@docs/context/project-layout.md
