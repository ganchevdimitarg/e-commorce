# Payment Service — Grade-A Remediation Design

**Date:** 2026-07-07
**Module:** `payment` (`com.ganchevdimitarg.payment`, port 8087, PostgreSQL)
**Branch:** `feat/order-boot4-migration` (current)
**Author:** brainstormed with the maintainer; to be executed via `subagent-driven-development`.

---

## 1. Context & Problem

The `payment` module was recorded in `CLAUDE.md` as *pre-Boot-4, does not compile (~112
migration hits)*. That note is **stale**. A large, high-quality refactor already sits
**uncommitted** in the working tree and, on a stable tree, **compiles and passes 18 unit
tests** on Boot 4.1.0 / Java 25 (`./mvnw -f payment/pom.xml clean verify` → BUILD SUCCESS).

The refactor already delivers:

- Boot-4 security: lambda DSL `SecurityFilterChain`, `@EnableMethodSecurity`, dual
  JWT/opaque-token `AuthenticationManagerResolver`, problem+json entry-point/denied handlers.
- A `PaymentGateway` **port** abstracting Stripe (`StripePaymentGateway` is the only class
  touching `com.stripe.*`; provider failures translate to `PaymentGatewayException`).
- A full `exception/` hierarchy (`BusinessException` + `NotFound`/`Conflict`/`Validation`) →
  `@RestControllerAdvice` → `application/problem+json`.
- Record command/response DTOs (`CreateChargeCommand`, `ChargeResponse`, …).
- A Redis `Idempotency-Key` interceptor guarding `/api/v1/payment/**`.
- `V2__add_audit_columns.sql` adding `created_at`/`updated_at`/`deleted_at`/`version`; an
  `Auditable` mapped-superclass; optimistic locking.
- A Testcontainers-Postgres `AbstractIntegrationTest` (no H2 for ITs).

**Problems this design closes:**

1. **The refactor is uncommitted** — it must be committed as a clean baseline before any
   task-by-task work builds on top, or dispatched implementers will sweep it into their
   commits.
2. **Cross-service regression:** `refund` was accidentally stripped from payment during an
   in-flight edit. `order`'s post-charge **compensation saga**
   (`order/.../ChargeServiceImpl.java:59` → `POST ${payment.service.charge.refund.post.uri}`,
   commit `48ff43e`) depends on payment's refund endpoint. Without it, a failed order
   confirmation after a successful charge takes money with **no order and no refund**.
3. **Charge safety:** `createCharge`/`createCard`/`createCustomer` call the external Stripe
   gateway **inside `@Transactional`**. A provider success followed by a DB-commit failure
   leaves an **orphaned provider-side object** (customer charged, no local record). No Stripe
   idempotency key is passed, so a retried charge can **double-charge** at the provider.
4. **Convention gaps:** entities use `@AllArgsConstructor` + `import lombok.*`; H2 test
   dependency + `application-test.properties` linger; the **reactor** resilience4j starter is
   on a WebMVC service; `application.yml` carries dead config.
5. **Testability gaps:** no maven-failsafe binding (so `*IT` is **excluded from the gate**);
   no JaCoCo coverage gate; no Redis Testcontainer despite Redis beans (idempotency has **zero
   test coverage**); gateway/security/error-envelope untested.

**Goal:** every one of the six audit dimensions scores ≥ 9/10 (Grade A, ≥ 90/100), the
refactor is committed as clean history, refund is a first-class **tested** endpoint, and the
double-charge / orphaned-charge risk is closed.

---

## 2. Global Constraints

- Package root `com.ganchevdimitarg.payment`; port 8087; PostgreSQL, own schema, Flyway-owned.
- British English in prose/comments; Conventional Commits subjects — **confirm every subject
  line with the maintainer before committing**.
- Records for all immutable DTOs/commands/responses; JPA entities use explicit Lombok
  (`@Getter @Setter @NoArgsConstructor` (+`@Builder`)) — never `@Data`, never
  `@AllArgsConstructor`, never `import lombok.*`.
- `@Transactional` on the service layer only; `@PreAuthorize` on the service layer only;
  repositories return `Optional<T>`, unwrapped via `orElseThrow`.
- All errors flow through `BusinessException` → `ControllerExceptionHandler` →
  `application/problem+json`. Never `Optional.get()` without a guard; never `assert` as a
  runtime guard.
- **Balances/amounts are strongly consistent — never cached** (root caching rules).
- Redis: `GenericJackson2JsonRedisSerializer` (never Java serialization); idempotency key
  `payment:idempotency:<key>` (existing convention in this module); TTL always set (24h).
- Flyway: `V<n>__snake_case.sql`, two underscores, monotonically increasing, never edit a
  committed migration; `ddl-auto=validate`; never `spring.flyway.enabled=false` in any profile.
  Existing max migration = **V2** → next = **V3**.
- Integration tests extend `AbstractIntegrationTest` (real Postgres + Redis via Testcontainers);
  never H2/embedded; no `Thread.sleep` (use Awaitility); test names `should_<behaviour>_when_<condition>`.
- Reactive types (`Mono`/`Flux`/WebFlux) are forbidden outside `gateway` — payment is WebMVC.
- **Build standalone** (root reactor is blocked): install `client` first
  (`./mvnw -f client/pom.xml install -DskipTests`), then
  `./mvnw -f payment/pom.xml clean verify`. Do **not** gate on `checkstyle:check` if the
  module carries pre-existing violations — verify via `clean verify`; run checkstyle for
  signal, not as a hard gate, matching the sibling-module ledgers.
- OAuth2 scope→authority mapping: token scopes `payment.read`/`payment.write` surface as
  authorities `SCOPE_payment.read`/`SCOPE_payment.write` (already wired in `ResourceServerConfig`).

---

## 3. Architecture & Component Boundaries

The refactor's layering is sound and is **preserved**, not redesigned:

```
controller/           thin REST adapters; @Valid on @RequestBody; no business logic
  ChargeController · CardController · CustomerController
service/ (+ impl/)    @Transactional + @PreAuthorize; orchestration; owns the DB write
  ChargeService · CardService · CustomerService
gateway/              PaymentGateway PORT + StripePaymentGateway ADAPTER (only com.stripe.* site)
  PaymentGateway · StripePaymentGateway · Gateway{Customer,Card,Charge,Refund} · CardDetails · ChargeRequest
dao/                  Spring Data repositories → Optional<T>, soft-delete-aware queries
  CustomerDao · CardDao · ChargeDao
domain/               JPA entities extending Auditable (audit cols + @Version)
  AppCustomer · AppCard · AppCharge · Auditable
dto/                  record commands/responses
exception/            BusinessException hierarchy + @RestControllerAdvice → problem+json
idempotency/          Redis Idempotency-Key interceptor (HTTP-level dedupe)
config/               security, MDC filter, Redis cache, OpenAPI, resilient introspector
```

**Boundary invariants this design enforces:**

- The service layer **never** sees `com.stripe.*` or `StripeException` — only the port and
  `PaymentGatewayException`.
- The external provider call happens **outside** the DB transaction; the local row is written
  **after** the provider confirms (charge-safety, Phase 2).
- Repositories return `Optional<T>`; callers `orElseThrow` a domain exception.
- Money amounts are never cached; idempotency/dedupe keys are the only Redis payload.

---

## 4. Design by Phase

Each phase below becomes one or more implementation-plan tasks. Phase 0 is executed directly
by the controlling session (it is groundwork over a pre-existing working tree, not a
dispatched task); Phases 1–5 run through `subagent-driven-development`.

### Phase 0 — Commit the green refactor as a baseline (controller-executed)

**Why first:** SDD dispatches fresh implementers who commit per task. A large uncommitted blob
must become committed history first, or unrelated changes get swept into task commits.

- Remove junk from the tree: `*.stackdump` crash dumps under `payment/` (and note the
  repo-wide ones for a separate cleanup — out of scope here).
- Stage **payment-only explicit paths** (never `git add -A`; never stage `authentication/`,
  `order/`, `notification/`, root `README.md`, etc. — those are separate concerns).
- **Exclude** untracked noise: `payment/.mcp.json`, `payment/MEMORY.md`, `payment/SETUP.md`,
  `payment/docs/` (generated context copies).
- Commit as a **small number of coherent Conventional Commits** whose subjects are confirmed
  with the maintainer first. Proposed slicing (each must leave the tree compiling; the
  interdependent core may be one atomic commit):
  1. `build(payment)!: migrate to Spring Boot 4 / Java 25` — pom changes, dependency set.
  2. `refactor(payment)!: Boot-4 core — security, gateway port, exception model, DTOs` —
     the interdependent core (package `excaption`→`exception` rename, delete dead
     catalog-derived DTOs/repositories/events/routing config, `ChargeServerImpl`→
     `ChargeServiceImpl` rename, records, security DSL, gateway port, services, controllers).
  3. `feat(payment): audit columns + optimistic locking (V2)` — `V2` migration + `Auditable`.
  4. `feat(payment): structured JSON logging` — `logback-spring.xml` + logstash encoder.
  5. `test(payment): Testcontainers unit + IT baseline` — test tree + `AbstractIntegrationTest`.

  *(If a clean multi-commit split proves infeasible from a single interdependent snapshot,
  fall back to one atomic `refactor(payment)!: Boot-4 Grade-A migration baseline` commit —
  confirmed with the maintainer. The number of commits is a preference, not a correctness
  requirement.)*
- **Gate:** `./mvnw -f payment/pom.xml clean verify` green at the baseline `HEAD`.

**Baseline `HEAD` after Phase 0 is the SDD base commit** for every subsequent task's review
package.

### Phase 1 — Restore refund as a first-class, tested endpoint

**Why:** closes the cross-service regression; `order`'s compensation saga must find a working
refund endpoint. The two deleted files are reconstructed from their (now-removed) call sites.

- **Create** `gateway/GatewayRefund.java`:
  ```java
  package com.ganchevdimitarg.payment.gateway;

  public record GatewayRefund(String id, String charge, String status) {
  }
  ```
- **Create** `dto/RefundChargeCommand.java`:
  ```java
  package com.ganchevdimitarg.payment.dto;

  import jakarta.validation.constraints.NotBlank;

  /** Request body for refunding a charge in full. */
  public record RefundChargeCommand(@NotBlank String chargeId) {
  }
  ```
- **Re-add to the port** `PaymentGateway`: `GatewayRefund refundCharge(String chargeId);`
- **Re-add to `StripePaymentGateway`** (full-refund semantics — no amount ⇒ Stripe refunds in
  full, which is what compensation requires):
  ```java
  @Override
  public GatewayRefund refundCharge(String chargeId) {
      return call(() -> {
          Refund refund = Refund.create(Map.of("charge", chargeId));
          log.info("Stripe refundCharge successful: {}", refund.getId());
          return new GatewayRefund(refund.getId(), refund.getCharge(), refund.getStatus());
      });
  }
  ```
- **Re-add to `ChargeService`**: `ChargeResponse refund(RefundChargeCommand command);`
- **Re-add to `ChargeServiceImpl`** (`@Transactional` + `@PreAuthorize("hasAuthority('SCOPE_payment.write')")`):
  load the local charge, 404 if unknown, call the gateway, return
  `new ChargeResponse(command.chargeId(), refund.status())`.
- **`ChargeDao.findByChargeId` → `Optional<AppCharge>`** (repositories return `Optional`, never
  null); the refund path unwraps via `orElseThrow(() -> new NotFoundException("Charge", …))`.
- **Re-add to `ChargeController`**: `POST /api/v1/payment/charge/refund-charge` →
  `@RequestBody @Valid RefundChargeCommand` → `ChargeResponse`.
- **Tests:** `ChargeServiceImplTest` — refund of a known charge returns the provider status;
  refund of an unknown charge id throws `NotFoundException` and never calls the gateway.
  `ChargeControllerTest` — `/refund-charge` returns 200 with the charge id; blank `chargeId`
  → 400. (Mocked gateway; no real Stripe.)

### Phase 2 — Charge safety (double-charge / orphaned charge)

**Why:** provider I/O inside a DB transaction risks an orphaned provider object on commit
failure, and a retry can double-charge. Decision: **Stripe idempotency key + gateway call
outside `@Transactional`.**

- **Thread an idempotency key to the provider.** The controller already receives
  `Idempotency-Key` (required by the interceptor). Pass it to the service and on to the
  gateway so Stripe dedupes provider-side. Extend the port's charge/customer/card creation to
  accept an idempotency key, and set Stripe's `idempotencyKey` request option in
  `StripePaymentGateway` (via `RequestOptions.builder().setIdempotencyKey(key).build()` on the
  `create` calls). *(Assumption to state at implementation: the key is the same value the HTTP
  interceptor guards on; scoped per operation.)*
- **Move the gateway call outside the transaction.** Restructure `createCharge` (and,
  consistently, `createCard`/`createCustomer`) so the provider call happens first, then a
  short transaction persists the confirmed result. The public orchestration method is **not**
  `@Transactional`; only the persistence step is. **Self-invocation caveat:** a `@Transactional`
  method invoked from another method of the *same* bean bypasses the Spring proxy and runs with
  **no** transaction. So the persistence step must be either (a) a method on a **separate**
  collaborator bean (e.g. a `ChargePersistence`/`@Component`) that the service injects and
  calls, or (b) wrapped in a `TransactionTemplate.execute(...)`. The plan must pick one
  explicitly — an in-bean call to a private/public `@Transactional` helper is **wrong** and
  silently non-transactional.
- **Tests:** provider-fails ⇒ nothing persisted; persist-fails-after-provider-success ⇒
  surfaced as a `500`/problem+json and the provider call is not retried within the same request
  (documents the orphan-reconciliation boundary); the Stripe idempotency key is passed through
  (verify via the mocked gateway capturing the key argument).

### Phase 3 — Convention cleanup

- **Entities** (`AppCustomer`, `AppCard`, `AppCharge`): drop `@AllArgsConstructor`, replace
  `import lombok.*` with explicit imports; keep `@Getter @Setter @NoArgsConstructor @Builder`.
  Verify builder-only construction still compiles (no positional-constructor callers).
- **Remove H2**: delete the `com.h2database:h2` test dependency and the `h2.version` property;
  delete `payment/src/test/resources/application-test.properties` **only if** nothing depends
  on the flags it sets (`eureka.client.enabled=false`, vault/cloud-compat toggles) — if those
  flags are still needed for the context to boot in tests, **relocate them** to an
  `application-test.yml`/`@DynamicPropertySource` rather than dropping them. (H2 removal is the
  requirement; the boot flags must survive.)
- **Resilience4j starter:** replace `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
  with the non-reactive `spring-cloud-starter-circuitbreaker-resilience4j` (payment is WebMVC;
  reactive is forbidden outside `gateway`). Confirm `ResilientOpaqueTokenIntrospector` (uses
  `CircuitBreakerRegistry`) and health `circuitbreakers.enabled` still resolve.
- **`application.yml` cleanup:** remove dead `logging.level.blog`; reconcile tracing config
  (drop legacy `spring.zipkin.base-url` in favour of the OTLP exporters already present, unless
  Zipkin is still a live target); add explicit `spring.data.redis` host/port.
- **Version pins:** where a dependency version is pinned locally but is Boot-BOM-managed, drop
  the local pin; genuinely module-specific versions (stripe-java, logstash, springdoc) may
  stay pinned locally with a one-line justification. *(No new dependency without a managed
  version.)*

### Phase 4 — Testability

- **maven-failsafe binding** in `payment/pom.xml` so `*IT` runs in `verify` (integration-test /
  verify goals). Confirm `CustomerPersistenceIT` executes in the gate afterwards.
- **Redis Testcontainer** in `AbstractIntegrationTest` (mirror the `order` base): a
  `GenericContainer("redis:7")` started in the static block, wired via `@DynamicPropertySource`
  (`spring.data.redis.host`/`.port`). Keep Postgres on the singleton `@ServiceConnection`
  pattern.
- **JaCoCo coverage gate**: 80% line overall, 100% on the `domain` model (match sibling
  modules). Add the plugin + `check` execution.
- **New tests** (each `should_<behaviour>_when_<condition>`):
  - `IdempotencyInterceptorIT` (Redis-backed): duplicate `Idempotency-Key` → 409; absent key on
    a write → 400; first request stores the key with TTL.
  - `StripePaymentGatewayTest`: a `StripeException` from a `create` call is translated to
    `PaymentGatewayException` (no `com.stripe.*` leaks out).
  - Security/`@PreAuthorize` regression: a `SCOPE_payment.read`-only principal calling a write
    method is denied; a problem+json 403 is produced.
  - Error-envelope on the wire: a domain exception yields `application/problem+json` with
    `code` + `timestamp` (via `ControllerExceptionHandler`).

### Phase 5 — Docs & final verification

- Append a dated entry to `payment/decisions.md` recording: the Boot-4 Grade-A migration,
  the charge-safety decision (Stripe idempotency key + call outside tx), the refund
  restoration, and the H2/reactor-starter removals.
- **Final gate:** `./mvnw -f payment/pom.xml clean verify` green (unit **and** IT via
  failsafe; JaCoCo satisfied); `checkstyle:check` for signal; re-score the audit to confirm
  ≥ 9 on every dimension.

---

## 5. Data Flow — createCharge (post-remediation)

```
Client ──POST /api/v1/payment/charge/create-charge (Idempotency-Key: K)──▶ ChargeController
  IdempotencyInterceptor: setIfAbsent(payment:idempotency:K, ttl=24h)
      ├─ duplicate ⇒ 409 ConflictException (problem+json)
      └─ first-seen ⇒ continue
  ChargeController.createCharge(@Valid CreateChargeCommand, K)
      ▼
  ChargeService.createCharge(command, K)              ← NOT @Transactional
      1. paymentGateway.createCharge(ChargeRequest, idempotencyKey=K)   ← provider I/O, OUTSIDE tx
             (Stripe dedupes on K ⇒ retry-safe)
      2. persistCharge(confirmedCharge)               ← @Transactional (short); writes AppCharge
      3. return ChargeResponse(id, status)
```

Provider failure ⇒ `PaymentGatewayException` ⇒ problem+json; nothing persisted. Persist
failure after provider success ⇒ problem+json 500; the charge exists provider-side under K and
is reconcilable, and a client retry with the same K does not double-charge.

---

## 6. Testing Strategy

- **Unit** (JUnit 5 + AssertJ + Mockito, mocked `PaymentGateway`): services and controllers,
  including refund happy/404 paths and charge-safety ordering.
- **Integration** (Testcontainers Postgres + Redis, `AbstractIntegrationTest`): persistence +
  soft-delete (`CustomerPersistenceIT`), idempotency interceptor, and any Redis-touching path.
- **Gate:** failsafe runs `*IT` in `verify`; JaCoCo enforces 80% line / 100% domain.
- No H2, no EmbeddedRedis, no `Thread.sleep`.

---

## 7. Target Scorecard

| Dimension | Before | Target | What moves it |
|---|---|---|---|
| Correctness & Safety | 6/10 | 9/10 | Charge outside tx + Stripe idempotency key (P2); refund restored so order's compensation works (P1); `Optional` on `findByChargeId`. |
| Convention compliance | 7/10 | 9/10 | Entities builder-only + explicit imports; H2 removed; non-reactive resilience4j; yml cleanup (P3). |
| Design & SOLID | 8/10 | 9/10 | Gateway port preserved; provider I/O separated from persistence (P2). |
| Design patterns | 8/10 | 9/10 | Idempotency at HTTP **and** provider level; adapter/port intact. |
| Readability & maintainability | 8/10 | 9/10 | Explicit imports, dead config/deps removed, decisions recorded. |
| Testability & tests | 5/10 | 9/10 | Failsafe gate + Redis IT + JaCoCo + idempotency/gateway/security/envelope tests (P4). |

**Projected: ≥ 90/100 — Grade A.**

---

## 8. Out of Scope / Follow-ups

- Repo-wide `*.stackdump` cleanup and the unstable Git Bash environment (only payment's are
  removed here).
- The uncommitted changes in `authentication`, `order`, `notification`, and root docs are
  **separate concerns** — this plan touches `payment/` paths only.
- HTTP-level idempotency remains store-key-only (409-on-duplicate, key burned for 24h on a
  failed first attempt) — consistent with `auth`/`order`/`catalog`; a move to true
  cached-response replay is a repo-wide follow-up, not payment-specific.
- Card-PII handling in payment requests (if any) is not re-architected here.

---

## 9. Self-Review Notes

- **Type consistency:** `refund(RefundChargeCommand) → ChargeResponse` used by service,
  controller, tests (P1); `GatewayRefund(String id, String charge, String status)` produced by
  the adapter, consumed by the service; `findByChargeId → Optional<AppCharge>` everywhere its
  caller unwraps (P1); charge/customer/card creation gains an idempotency-key parameter
  threaded controller→service→gateway (P2).
- **Migration numbering:** existing max is `V2`; no new migration is required by this plan
  (all changes are code/pom/config/test). If any phase needs schema change, it takes `V3`.
- **No reactive leak:** P3 removes the only reactive dependency; payment stays WebMVC.
- **Money-safety invariant:** no amount/balance is cached; the only Redis payload is the
  idempotency dedupe marker.
