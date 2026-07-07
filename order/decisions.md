# Decision Log

Running log of architectural and technical decisions. For formal ADRs see `docs/adr/`.
Reference with `@docs/decisions.md` in prompts when Claude re-raises settled questions.

| Date | Decision | Alternatives rejected | Reason |
|---|---|---|---|
| 2025-06 | WebMVC for business services; WebFlux for api-gateway only | WebFlux everywhere | Virtual threads give WebMVC near-reactive throughput; simpler testing; team familiarity. See ADR-001. |
| 2025-06 | Avro + Schema Registry for all Kafka messages | JSON, Protobuf | Schema enforcement, backward compat checking, Confluent tooling. See ADR-002. |
| 2025-06 | Records as default immutable type | Lombok @Value everywhere | Language feature; no annotation processing; cleaner compact constructors. See ADR-003. |
| 2025-06 | Choreography sagas via Kafka | Conductor orchestrator, 2PC | No single point of failure; works across PG + Mongo; no extra infra. See ADR-004. |
| 2025-06 | Soft-delete via deleted_at column | Hard DELETE, status column | Audit trail; event replay; no accidental data loss; consistent across all tables. |
| 2025-06 | Flyway for all schema changes; ddl-auto=validate | Liquibase, Hibernate auto-DDL | Flyway simpler API; validate catches drift early; industry standard for Spring. |
| 2025-06 | Redis cache-aside over Spring @Cacheable | @Cacheable | Explicit TTL control; explicit serialization; easier to test; no magic. |
| 2025-06 | Idempotency-Key header on all mutating endpoints | None (at-most-once) | Prevents duplicate charges/orders on network retry; required for Kafka consumer safety. |
| 2025-06 | Testcontainers for all integration tests | H2, EmbeddedMongo | Tests run against real engines; no behaviour divergence; catches index/type issues. |
| 2025-06 | Resilience4j for circuit breaking | Hystrix (EOL), manual | Active project; Spring Boot 4 native support; annotation-driven; no extra infra. |
| 2025-06 | Virtual threads (Java 25 default) over platform threads | Reactive WebFlux everywhere | Simplifies code; `ScopedValue` replaces `ThreadLocal`; Spring Boot 4 native; no reactive complexity outside api-gateway |
| 2025-06 | Resilience4j defaults as documented (50%/2s/30s/5 calls/10 concurrent/5s timeout) | Per-service customisation from day one | Sane starting point; services override in `application.yml` only when measured data demands it |
| 2025-06 | Idempotency key scoped to service, never per-user | Per-user idempotency key | Prevents cross-user replay; keeps idempotency purely about network retries, not authorisation |

- 2026-06-20 — Convention rules have a single source of truth: `CLAUDE.md` + its `@import` files. Skills and agents reference them and govern process only; they do not restate rules. `review/SKILL.md`'s severity checklist is the one allowed alternative representation (it adds Critical/Warning/Suggestion tagging). Rationale: kill three-way drift flagged in the 2026-06-20 setup audit.
- 2026-06-21 — The project-local `.claude/skills/brainstorming/` fork is canonical; `superpowers:brainstorming` is superseded in this repo. Its `description:` keeps the upstream activation trigger verbatim but appends a "Project-canonical fork" marker so the two skills no longer share byte-identical activation text. Alternatives rejected: keeping the description byte-identical (left activation non-deterministic — the plugin copy could fire instead of the 97/100 fork). Rationale: closes the dual-activation ambiguity flagged in the 2026-06-21 setup evaluation.

## 2026-07-04 — Order grade-A remediation

- Charge amount computed as Σ(price × quantity) in integer cents; never string-strip a `BigDecimal`.
- `order_number` assigned from Postgres sequence `order_number_seq` (instance-safe), not an in-memory counter.
- Errors render as RFC 9457 problem+json via a `BusinessException` hierarchy (mirrors catalog/payment).
- Bean Validation on `OrderDto` replaces the bespoke (and dead) `@ValidationRequest` aspect.
- `@PreAuthorize` moved to the service layer; method security enabled.
- Header-based `MdcRequestFilter` + Kafka trace/correlation headers; Kafka topic renamed `"sentMail"` → `order.notification.requested` (2026-07-05, coordinated with notification).
- `excaption` package renamed to `exception`.
- Boot-4 autoconfig gaps closed (surfaced by the new Testcontainers IT): `flyway-core` → `spring-boot-starter-flyway` (+`flyway-database-postgresql`) so migrations actually run; `spring-security-oauth2-client` → `spring-boot-starter-oauth2-client` so a `ClientRegistrationRepository` is built; dropped `spring.jackson.serialization.write-dates-as-timestamps` (the enum constant was removed in Jackson 3). Without these the application context could not start on Boot 4.
- Testcontainers `OrderPersistenceIT` (singleton Postgres, `@ServiceConnection`) verifies the sequence and soft-delete round-trip; the `test` profile disables Vault/config/eureka and uses an explicit OAuth2 `token-uri` (no eager OIDC discovery). Note: `checkstyle`/`flyway:validate`/JaCoCo are not wired for this module (catalog carries those); the IT's live V1–V5 migration run is the migration check.

## 2026-07-06 — Final-review remediation

Fixes from the gating whole-branch review before merge.

- **Compensating refund now hits a real endpoint (was a Critical):** the payment service
  gained `POST /api/v1/payment/charge/refund-charge` (`PaymentGateway.refundCharge` →
  Stripe full refund; unknown charge id → `404`). Previously `createOrder`/`cancelOrder`
  called a non-existent endpoint, so a charged customer was never actually refunded.
- **Refund is a full refund:** dropped the hard-coded `0L` amount from
  `ChargeService.refund`. Compensation always returns the entire captured charge, so no
  amount is sent and Stripe refunds in full.
- **`cancelOrder` refund moved outside the transaction:** the order (with its charge) is
  loaded read-only via `OrderPersistence.loadOwnedWithCharge`, the refund runs with no
  transaction open, then the `CANCELLED` transition commits in a fresh one — no DB row lock
  is held across the payment round-trip (mirrors the `createOrder` seam).
- **Order-confirmation email moved out of the idempotency guard:** the guarded unit is now
  just the charge+confirm, so a mail failure can no longer prevent caching and cause a
  duplicate charge on retry (`OrderController.createOrder`).
- **Actuator lockdown:** only `/actuator/health/**` is public; every other actuator endpoint
  requires authentication.
- **Bean-validation errors render as problem+json:** `handleMethodArgumentNotValid` now emits
  the same `code`/`timestamp` shape as every other error.

Deferred (blocked, tracked here):

- **Notification DLT / `@RetryableTopic`:** the consumer group was renamed
  `notification` → `notification-group`, but the dead-letter topic + retry backoff are
  deferred to the notification Boot-4 migration. That module's pre-Boot-4 spring-kafka has
  no `RetryableTopic` and no `spring-retry` on the classpath, so wiring the DLT now would
  introduce a non-compiling pattern into an already-lagging module. Land it with the
  notification `javax→jakarta` / Boot-4 upgrade.

## 2026-07-05 — Grade-A remediation

- **Payment consistency (was a Critical):** `createOrder` no longer wraps the external
  charge in a DB transaction. Order + items commit first (`OrderPersistence.placeOrder`),
  the card is charged outside any transaction, then the charge + `PAID` transition commit
  in a second transaction (`confirmPaid`). A post-charge failure triggers a best-effort
  compensating refund and moves the order to the new terminal `PAYMENT_FAILED` state — no
  path can take money without a confirmed order or a refund. Alternatives rejected:
  charge-in-transaction (strands money on rollback); full transactional-outbox + reconciler
  (larger scope; compensation stays best-effort until then).
- **Transaction seam:** DB writes extracted to a separate `OrderPersistence` bean so each
  is its own boundary (self-invocation would not honour `@Transactional`); it is also the
  single authority for guarded status transitions — create/cancel/advance delegate to it.
- **Dependency failures:** a tripped circuit breaker / failed outbound call now surfaces as
  `503 ServiceUnavailableException`, never a silent empty sentinel a caller could mistake
  for a valid "not found" (which previously let a profile blip trigger spurious user creation).
- **Structured logging:** `logback-spring.xml` + `logstash-logback-encoder` (pinned in the
  root BOM) emit JSON with the MDC correlation fields.
- **Security:** CSRF explicitly disabled on the stateless resource server; `isJwt` guards a
  null `Authorization` header (was a 500).

## 2026-07-04 — Deferred, cross-service — RESOLVED 2026-07-05

1. **Kafka topic rename** `sentMail` → `order.notification.requested` — **done**.
   `KafkaTopics.ORDER_NOTIFICATION_REQUESTED`; the notification `@KafkaListener` was updated
   in lockstep. Profile had no producer to the old topic (a dead `kafka-settings.topics.mail`
   yml key), so no third party was affected.

2. **Card-PII relocation out of `OrderDto`** — **done**. `OrderDto` slimmed to
   `username`/`deliveryComment`/`items`; all `cardNumber`/`cardCvc`/`cardExp*`/PII removed
   along with the lazy `createProfileUser` path. Order now requires a pre-registered user +
   card and returns `409` if the profile is missing — removing all PAN/CVC from the service.
   Follow-up still open: the profile response (`UserDto`) itself still carries `cardCvc`.
