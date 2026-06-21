# catalog-service — best-practices roadmap (design spec)

- **Date:** 2026-06-21
- **Module:** `catalog` (`com.concordeu.catalog`), `catalog-service`, port 8084
- **Parent:** `com.concordeu:e-commerce` — Spring Boot 4.0.2, Java 21
- **Status:** approved structure; ready to decompose into per-phase implementation plans
- **Author:** brainstorming session

## Purpose

Bring `catalog-service` up to the best practices expected of an OAuth2 resource-server /
product-catalogue microservice on Spring Boot 4. The service works functionally but carries
Boot 2.x idioms, a weak error model, and test infrastructure that never exercises the real
schema. This document is a **prioritised, phased roadmap** — not a single implementation plan.
Each phase is independently shippable and becomes its own `writing-plans` plan.

## Scope decisions (from brainstorming)

1. **Shape:** comprehensive roadmap, decomposed into ordered phases (correctness → hardening → scale).
2. **Infrastructure:** recommend infra per item; anything requiring new infra (Redis, Kafka) is
   **flagged and deferred to an implementation-time decision**, never assumed.
3. **Out of scope:** changes to sibling modules (gateway, authentication, etc.); introducing
   MongoDB; rewriting the public API contract beyond the response-shape fixes listed here.

## Success criteria

- `./mvnw clean verify -pl catalog -am` is green on Boot 4.0.2 / Java 21 with **no `javax.*`**
  imports and **no deprecated Spring Security 5 DSL**.
- All error responses are `application/problem+json` (RFC 9457) with correct HTTP status.
- Integration tests run on **Testcontainers + real Flyway migrations** (no H2, no `create-drop`).
- No business data is hard-deleted; every table has audit columns.
- Traces, metrics and logs are exported over **OTLP** and viewable in Grafana with trace↔log
  correlation; the legacy Zipkin config is gone.
- The convention deltas in `CLAUDE.md` § "Known migration gaps" are all closed (or explicitly deferred).

---

## Phase 0 — Boot 4 correctness  *(P0 — blocks everything; likely non-compiling today)*

The pom inherits Spring Boot 4.0.2 / `jakarta.validation`, but the source still uses the
Java EE namespace and Spring Security 5 DSL, so the module is at best fragile and at worst
does not compile cleanly against its parent.

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 0.1 | `javax.persistence.*`, `javax.validation.*`, `javax.servlet.*` (4 files) | `jakarta.*` equivalents | No `javax.` import remains in `src/main`; module compiles |
| 0.2 | Security 5 DSL: `authorizeRequests()`, `mvcMatchers()`, `.and()`, `@EnableGlobalMethodSecurity`, non-lambda `oauth2ResourceServer()` | Security 6 DSL: `authorizeHttpRequests()`, `requestMatchers()`, lambda config, `@EnableMethodSecurity(prePostEnabled = true)` | `ResourceServerConfig` uses only non-deprecated APIs; auth behaviour unchanged (JWT + opaque resolver preserved) |
| 0.3 | `springdoc-openapi-ui` 1.6.13 (Boot 2 line) | `springdoc-openapi-starter-webmvc-ui` 2.x | Swagger UI + `/v3/api-docs` load; OAuth2 PKCE flow still configured |
| 0.4 | Hotfix pulled forward: `isJwt()` dereferences a possibly-null `Authorization` header → 500 | Null/blank guard → treat as non-JWT (opaque path), no NPE | Unauthenticated request returns 401, not 500 |

**Acceptance (phase):** `./mvnw clean verify -pl catalog -am` green; manual smoke of a protected
and a public endpoint. **Infra:** none.

---

## Phase 1 — Error model & API contract  *(P0/P1)*

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 1.1 | No domain exception hierarchy | `BusinessException(HttpStatus, code, message)` + `NotFoundException` (404), `ConflictException` (409), `ValidationException` (400) | Hierarchy exists in `…exception`; subclasses carry status + code |
| 1.2 | `ControllerExceptionHandler` only catches `IllegalArgumentException`, returns 400 body with 404 status, uses `java.util.Date`; everything else is a raw 500 | `@ControllerAdvice` → `application/problem+json` (RFC 9457) for `BusinessException`, Bean Validation (`MethodArgumentNotValidException`), auth (401/403), and a safe fallback | Each mapped exception returns the correct status + problem+json body; ISO-8601 timestamps; no raw 500 for known failures |
| 1.3 | `IllegalArgumentException` thrown across `ProductServiceImpl`, `CategoryServiceImpl`, validators | Replaced with the appropriate `BusinessException` subclass (not-found → `NotFoundException`, duplicate → `ConflictException`) | No `IllegalArgumentException` thrown for domain control flow |
| 1.4 | No `@Valid` on `@RequestBody`; `ProductDataValidator`/`CommentDataValidator` duplicate entity `@Size` rules in the service layer | `@Valid` on every `@RequestBody`; constraints live on record components / entity fields; service-layer validators removed or reduced to genuine cross-field rules in compact constructors | Invalid payload returns 400 problem+json before the service runs; validators no longer duplicate Bean Validation |

**Infra:** none.

---

## Phase 2 — Persistence & domain hardening  *(P1)*

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 2.1 | `products`/`categories`/`comments` have no audit columns | New Flyway `V4__add_audit_columns.sql`: `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `deleted_at TIMESTAMPTZ NULL` | Migration applies; `flyway:validate` clean; entities map the columns (e.g. `@CreationTimestamp`/`@UpdateTimestamp`) |
| 2.2 | Hard delete via `deleteByName` | Soft-delete: set `deleted_at = now()`; all reads filter `deleted_at IS NULL` (`@SQLRestriction` or explicit queries) | Deleting hides the row from all read paths; row persists in DB |
| 2.3 | Controllers return `Page<ProductResponseDto>`; raw `@RequestParam int page, int size` | `PageResponse<T>` record; `@PageableDefault(size = 20)`; `@Max(100)` on size | No raw `Page` crosses the API; oversized `size` rejected with 400 |
| 2.4 | Entities carry `@Builder`/`@AllArgsConstructor` + mutable setters; no optimistic locking; `products.stock` is `bit` | Trim entity Lombok to `@Getter/@Setter/@NoArgsConstructor`; add `@Version`; migrate `stock` to `boolean` | Entities follow the entity-Lombok rule; concurrent update conflict surfaces as 409 |
| 2.5 | Package `…excaption`; repositories named `*Dao` | Rename to `…exception`; `*Dao → *Repository` | Names match `CLAUDE.md` conventions; all references updated |

**Infra:** none (Postgres only). **Note:** 2.5 is a wide rename — do it in its own commit to keep diffs reviewable.

---

## Phase 3 — Security & resilience depth  *(P1/P2)*

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 3.1 | `getProductsById` POST endpoint has no `@PreAuthorize`; authorisation placement is inconsistent | Every endpoint authorised on the correct `SCOPE_catalog.read` / `SCOPE_catalog.write`; decide and apply one consistent `@PreAuthorize` layer | No unprotected mutating/read endpoint; scope mapping documented |
| 3.2 | `management.endpoints.web.exposure.include: "*"` exposes everything | Curated exposure (`health,info,prometheus`); health details internal-only | Sensitive actuator endpoints not publicly reachable |
| 3.3 | Outbound/introspection calls lack a circuit breaker | Resilience4j `@CircuitBreaker` (+ fallback) on outbound HTTP (token introspection, any inter-service call) | Breaker visible at `/actuator/health`; fallback returns a typed failure, not a 500 |
| 3.4 | No request-scoped tracing context / plain-text logs | `OncePerRequestFilter` setting MDC `traceId/spanId/userId/serviceId` (cleared in `finally`); structured JSON logging **(JSON-logging target superseded by Phase 6.3 — the MDC filter is still required and feeds 6.4)** | Log lines carry trace + user context; format is JSON |
| 3.5 | Blocking WebMVC service runs on the default Tomcat **platform-thread pool**; concurrency is capped by pool size and needs tuning, and every blocking JPA/outbound call holds a platform thread for the request's duration | Enable virtual threads globally (`spring.threads.virtual.enabled=true`); route `@Async`/task executors through virtual threads (Boot's `AsyncTaskExecutor` / `Executors.newVirtualThreadPerTaskExecutor()`); keep Resilience4j on **semaphore `@Bulkhead`** (never `ThreadPoolBulkhead`) and move call timeouts to the HTTP client / `@TimeLimiter` rather than pool sizing; confirm MDC (3.4) and `ScopedValue` context propagate per virtual thread | `spring.threads.virtual.enabled=true`; request threads are virtual (verified via thread dump / `Thread.currentThread()` log). No `ThreadPoolBulkhead` in use; circuit-breaker + semaphore-bulkhead behaviour unchanged. MDC trace/user context still present on log lines under load. No `jdk.VirtualThreadPinned` JFR events under a load test on Java 25 |

**Why virtual threads (not platform threads or reactive):** catalog is a blocking WebMVC stack (JPA + outbound REST + token introspection) — the textbook virtual-thread use case, giving per-request scalability without tuning a Tomcat pool. It is already the stated convention in `CLAUDE.md` ("Virtual threads for blocking I/O fan-out"). Platform threads (status quo) are safe but cap concurrency at pool size and need tuning — only worth keeping under heavy `synchronized`/native pinning, which catalog does not have. Reactive (`Mono`/`Flux`) is off the table — `CLAUDE.md` forbids reactive in business logic. The flag itself is one line; the real work is the Resilience4j and context-propagation gotchas above.

**Infra:** none (Zipkin/Micrometer already present).

---

## Phase 4 — Testing & observability  *(P1/P2)*

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 4.1 | Tests use H2 + `ddl-auto=create-drop` (`application-test.properties`) — never exercise real migrations | `AbstractIntegrationTest` with Testcontainers `postgres:16` + real Flyway; remove H2 dep and `create-drop` | Integration tests run against Postgres via the committed migrations; H2 gone |
| 4.2 | Test names like `createProductShouldCreateNewProduct` | `should_<behaviour>_when_<condition>` | Existing tests renamed; new tests follow the pattern |
| 4.3 | No coverage gate | 80% line · 100% domain logic, enforced in build | Build fails below threshold |
| 4.4 | No domain metrics | `MeterRegistry` counters `catalog.<entity>.<action>` (e.g. `catalog.product.created`) | Metrics present at `/actuator/prometheus` |

**Infra:** Testcontainers (test scope, Docker required for the test run).

---

## Phase 5 — Scale & eventing  *(P2 — infra approved; Redis + Kafka)*

These are genuine best practices for the service type and were infra-gated; the infrastructure
decision is now **resolved YES** (see below), so all three items are committed.

| # | Best practice | Recommendation | Defer-if |
|---|---|---|---|
| 5.1 | `Idempotency-Key` on POST/PUT/DELETE | **Add Redis** (monorepo-aligned) keyed `catalog:idempotency:<key>`, 24h TTL; **DB-table fallback** if Redis is rejected | No appetite for Redis and no observed duplicate-submit problem |
| 5.2 | Read caching for catalogue (read-heavy, rarely mutated) | **Add Redis** cache-aside (`catalog:product:<id>`, TTL + write-through invalidation) | Measured DB read latency is acceptable → **skip (YAGNI)** |
| 5.3 | Publish `Product{Created,Updated,Deleted}` domain events | **Kafka** with Avro (`catalog.product.*`) **only if a consumer service actually needs them** | No downstream consumer exists → **defer (YAGNI)** |

**Decision (resolved 2026-06-21): YES to both.** The platform will give catalog its own Redis
instance, and at least one service consumes catalogue change events. Phase 5 is therefore
**committed scope, not gated** — implement all three items:

- **5.1 Idempotency** — Redis-backed (`catalog:idempotency:<key>`, 24h TTL); the DB-table fallback is dropped.
- **5.2 Read caching** — Redis cache-aside (`catalog:product:<id>`, TTL + write-through invalidation).
- **5.3 Domain events** — publish `Product{Created,Updated,Deleted}` to **Kafka** with Avro (`catalog.product.*`),
  since a downstream consumer exists.

This pulls Redis and Kafka into the monorepo's root docker-compose. **Note:** `CLAUDE.md` currently
says "No Kafka, MongoDB, Redis, or Avro in catalog" — that constraint is now superseded for catalog
by this decision; update `CLAUDE.md` when Phase 5 lands so the convention doc matches reality.

---

## Phase 6 — OpenTelemetry observability  *(P1/P2)*

Observability is effectively broken on Boot 4 today: tracing is dead (stale `spring.zipkin.base-url`,
no `micrometer-tracing` bridge dependency, so no spans are exported), the `prometheus` actuator
endpoint is enabled but `micrometer-registry-prometheus` is **not** on the classpath, and logs are
plain text with no trace correlation. This phase makes traces + metrics + logs work over **OTLP**
via the **Micrometer OpenTelemetry bridge** (idiomatic, keeps `@Observed`/Micrometer and the
`catalog.<entity>.<action>` metric names from 4.4), viewed in Grafana with trace↔log correlation.

Split into **6.A** (app-side, ships independently) and **6.B** (backend infra).

### 6.A — App-side instrumentation  *(deps + config only; no catalog-runtime infra)*

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 6.1 | No tracing bridge; dead `spring.zipkin.base-url`; no spans exported | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`; remove the Zipkin block; set `management.otlp.tracing.endpoint` + `management.tracing.sampling.probability` | Spans exported over OTLP; W3C `traceparent` propagated on outbound HTTP; end-to-end trace visible |
| 6.2 | `prometheus` actuator endpoint enabled but `micrometer-registry-prometheus` not on the classpath | Add `micrometer-registry-prometheus`; keep Prometheus scrape for metrics (Phase 4.4 names unchanged) | `/actuator/prometheus` returns metrics incl. `catalog.*` counters |
| 6.3 | Plain-text logs, no trace correlation (**supersedes 3.4's "structured JSON logging" target**) | `opentelemetry-logback-appender-1.0` exporting logs over OTLP with `traceId`/`spanId` injected; keep a JSON console appender for local/dev | Log records arrive in the backend correlated to their trace; console stays readable locally |
| 6.4 | `traceId`/`spanId` not in MDC for app log lines | Bridge OTel context → MDC (`traceId,spanId,userId,serviceId`) via the Phase 3.4 filter; one source of truth for correlation IDs | Every log line carries the live trace's IDs; matches the span in the backend |

**Acceptance (6.A):** with the collector endpoint configured, catalog emits traces + metrics +
correlated logs; no dead Zipkin config remains. Ships independently of 6.B (can export to any OTLP sink).

### 6.B — Backend stack  *(new monorepo infra in root docker-compose)*

**Decision (resolved 2026-06-21): YES.** The platform will own a Grafana/Tempo/Loki stack and
**retire Zipkin**. This is therefore **committed scope**. Added to the **root** docker-compose
(monorepo-level platform infra, not catalog-only):

| # | Component | Role | Notes |
|---|---|---|---|
| 6.5 | OpenTelemetry Collector | OTLP ingest + routing/fan-out | Single vendor-neutral ingest point; backends swap without app config |
| 6.6 | Tempo | Trace store | **Replaces Zipkin :9411** (remove Zipkin from compose) |
| 6.7 | Loki | Log store | Receives logs via the collector |
| 6.8 | Grafana | Single UI | Datasources Tempo/Loki/Prometheus; trace↔log correlation panels |

**Relations:** supersedes Phase 3.4's JSON-logging *target* (3.4's MDC work is still required and
feeds 6.4); Phase 4.4 metrics are unchanged but now also viewable in Grafana; 6.B is monorepo-wide
platform infra — an explicit, approved exception to the "no sibling-module changes" scope rule
because it is shared infra, not module code.

---

## Phase 7 — Read scaling (read replica)  *(P2 — infra approved; Postgres replica)*

Route read-only traffic to a streaming replica, keeping writes and Flyway on the primary.
Complements Phase 5.2 (the Redis cache absorbs hot reads; the replica absorbs cache misses and
large scans). Catalogue reads tolerate mild staleness, which makes a replica safe here.

| # | Problem | Target | Acceptance |
|---|---|---|---|
| 7.1 | Single Postgres datasource carries all read + write load | `postgres-primary` + `postgres-replica` (streaming replication) in **root** docker-compose; catalog gets a second JDBC URL (reader) | Replica streams from primary; both reachable from catalog |
| 7.2 | No read/write datasource separation | `LazyConnectionDataSourceProxy` → `AbstractRoutingDataSource` keyed on `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`; two HikariCP pools (writer/reader); **Flyway + all writes pinned to primary** | `readOnly` tx resolves to the replica pool, write tx to primary (verified via `pg_stat_activity` / datasource metric); migrations never touch the replica |
| 7.3 | Read paths not marked, so routing can't engage | `@Transactional(readOnly = true)` on every query service method; assert no write occurs inside a `readOnly` tx | All product/category/comment reads run on the replica; an accidental write in a readOnly tx fails fast |
| 7.4 | Replication lag breaks read-your-writes (create → immediate GET hits stale replica) | Documented staleness policy: browse/list reads → replica (staleness OK); read-after-write confirmation → primary. Pair with **5.2 cache write-through _population_** (not just invalidation) so a just-written entity is served from cache, not a stale replica. **Graceful degradation:** if the replica is unhealthy, route reads to primary — never fail the request | Stale-read window documented; just-written entity readable immediately; replica outage degrades to primary reads with no 500s |

**Infra:** Postgres replica (streaming replication) in root compose — approved.
**Relations:** complements 5.2 (cache + replica are layered, not alternatives); depends on Phase 2
soft-delete (`deleted_at` filter applies on both nodes). Sequence **after Phase 5**, because 5.2's
write-through population is the read-your-writes mitigation in 7.4.

---

## Summary — all items

| Phase | Items | Priority | New infra |
|---|---|---|---|
| 0 — Boot 4 correctness | jakarta, Security 6 DSL, springdoc 2.x, isJwt NPE | P0 | none |
| 1 — Error model & contract | BusinessException, problem+json, `@Valid` | P0/P1 | none |
| 2 — Persistence & domain | audit cols, soft-delete, PageResponse, `@Version`, renames | P1 | none |
| 3 — Security & resilience | authorise all, actuator lockdown, circuit breaker, MDC/JSON logs, virtual threads | P1/P2 | none |
| 4 — Testing & observability | Testcontainers+Flyway, test naming, coverage gate, metrics | P1/P2 | Testcontainers (test) |
| 5 — Scale & eventing | idempotency, caching, domain events | P2 | Redis + Kafka (approved) |
| 6 — OpenTelemetry observability | OTel bridge, OTLP traces/metrics/logs, retire Zipkin | P1/P2 | OTel Collector + Tempo/Loki/Grafana (approved) |
| 7 — Read scaling | read replica, read/write routing, lag-aware reads | P2 | Postgres replica (approved) |

## Sequencing & dependencies

- **0 → 1 → 2** are strictly ordered: correctness must land before the error model, which the
  persistence changes depend on (soft-delete returns `NotFoundException`, etc.).
- **3 and 4** can proceed in parallel after Phase 1; Phase 4's Testcontainers work should land
  early so subsequent phases are validated against the real schema.
- **3.5 (virtual threads)** lands after 3.3 (circuit breaker) so the bulkhead/timelimiter interplay
  is settled first, and is aligned with the `authentication-update-java-version-25` migration —
  full benefit (no `synchronized` pinning, JEP 491) requires Java 25; on Java 21 it can be enabled
  with the pinning caveat.
- **5** is independent (infra now approved); do not start it before 0–2 ship.
- **6.A (app-side OTel)** depends on Phase 3.4's MDC filter (it feeds 6.4) and supersedes 3.4's
  JSON-logging target — sequence 6.A after 3.4. **6.B (backend stack)** is monorepo infra and can
  proceed in parallel; retire Zipkin only once Tempo is receiving traces. 6.A ships even if 6.B lags.
- **7 (read replica)** sequences after Phase 5: it depends on 5.2's cache write-through population as
  the read-your-writes mitigation (7.4) and on Phase 2 soft-delete reads. Do not start before 0–2 ship.

## Risks

- Phase 0 may surface a larger blast radius than expected if other modules share types via the
  `client` lib — verify `client` is already on jakarta before migrating catalog.
- Phase 2.4 (`stock bit → boolean`) and 2.5 (package/`Dao` renames) are wide-touch; isolate per commit.
- Phase 4.1 makes Docker a hard prerequisite for the test run — confirm CI runners have it.
- Phase 3.5: the `@TimeLimiter`/`ThreadPoolBulkhead` patterns shown in `docs/context/resilience.md`
  assume a managed pool; under virtual threads prefer semaphore bulkhead + client-level timeouts,
  otherwise you reintroduce a bounded pool that defeats the point.
- Phase 5 contradicts `CLAUDE.md`'s "No Kafka/Redis/Avro in catalog" rule; the convention doc must
  be updated when Phase 5 lands so guidance and reality do not diverge.
- Phase 6: tracing sampling at 100% plus OTLP log export can be costly under load — set a sensible
  `management.tracing.sampling.probability` and size Loki retention. The OTel Collector is a single
  ingest point; treat it as critical infra (the app should degrade gracefully if it is unreachable,
  never block requests on telemetry export).
- Phase 7: replication lag yields stale reads — the staleness policy and 5.2 write-through population
  (7.4) must land together, or read-after-write will regress. A routing bug that sends a write into a
  `readOnly` tx silently hits the replica and fails — enforce read-only and test it. Size the two
  Hikari pools independently; plan replica-failover behaviour (degrade reads to primary).

## Out of scope / explicitly not doing

- MongoDB, GraphQL, or API redesign beyond response-shape fixes.
- Changes to gateway/authentication/other **module code** — note that Phases 5 and 6 do add shared
  **platform infra** to the monorepo's root docker-compose (Redis, Kafka, OTel Collector,
  Tempo/Loki/Grafana, Postgres replica) and retire Zipkin; that is approved and is distinct from
  editing sibling modules.
