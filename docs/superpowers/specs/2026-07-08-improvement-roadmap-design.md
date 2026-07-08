# Improvement roadmap — gRPC, Kafka sagas, and remaining remediation

**Date:** 2026-07-08
**Status:** Approved (interview-driven design; decisions confirmed by the project owner)

## Why

The repo should reach the production-readiness bar described in `evaluationAndScore.md`.
Three headline goals came out of the brainstorming interview:

1. Introduce **gRPC** between services — motivated by learning/portfolio value,
   performance, and type-safe generated contracts.
2. Use **Kafka** properly for cross-service writes — **choreography sagas** plus
   **reliability hardening** (DLT, retries, idempotent consumers, outbox).
   Avro/Schema Registry stays deferred (ADR-002 unchanged).
3. Finish the remaining planned work (notification Boot-4 remediation,
   readiness-review residuals).

## Confirmed decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Foundations first — remediation before gRPC | gRPC needs a shared contracts module; easiest on a clean, fully building base |
| 2 | gRPC for **internal sync reads only**; gateway edge stays HTTP/JSON | Standard pattern; Spring Cloud Gateway gRPC support is limited |
| 3 | **gRPC reads, saga writes** — order→payment charge/refund becomes a choreography saga | Enforces the root rule "async Kafka events for cross-service writes" |
| 4 | Kubernetes is the target runtime | gRPC discovery must be config/DNS-driven, not Eureka-coupled |
| 5 | Secret-history purge of `bootsecurity.p12` happens in Phase 1 | Rotate first, then `git filter-repo`; earlier = less disruption |
| 6 | Profile's reactive→WebMVC rework folds into the gRPC phase | One disruption instead of two; card PAN/CVC DTOs dropped at the same time |
| 7 | gRPC stack = official **Spring gRPC** (`org.springframework.grpc`) | Spring-team project aligned with Boot 4; `net.devh` starter rejected (community, lags Boot) |

## Current state (explored 2026-07-08)

- authentication, catalog, gateway, order, payment: migrated and remediated on `main`;
  root reactor builds green.
- **notification** is the only module with a pending plan
  (`docs/superpowers/plans/2026-07-05-notification-boot4-remediation.md`): legacy
  springfox/webflux/h2 pom, `excaption` typo, no DLT/retry, no tests. In flight on
  `feat/notification-boot4-migration`.
- East-west REST is the weak spot: no client timeouts, one shared circuit breaker for
  three dependencies, hardcoded `127.0.0.1` URLs, empty-sentinel fallbacks.
- Kafka is JSON-only; auth has the reference transactional outbox; `catalog.product.*`
  topics have no consumers; notification's consumer has no DLT.
- No gRPC/protobuf anywhere yet.

## Roadmap

### Phase 1 — Foundations & hardening
Finish notification remediation; east-west resilience fixes (timeouts, per-dependency
circuit breakers, fail-fast fallbacks, externalized URLs); secret rotation + history
purge; catalog N+1 quick win; stale CLAUDE.md cleanup.
Detailed plan: `docs/superpowers/plans/2026-07-08-phase-1-foundations.md` (see the
approved session plan).

### Phase 2 — Kafka reliability
DLT + retry/backoff standardized on every consumer; idempotent consumers (Redis guard
keyed on event id); transactional outbox for order and payment producers (reuse auth's
`OutboxRelay` pattern); wire `auth.password.reset-requested` → notification; document
`catalog.product.*` as public integration events or retire them.

### Phase 3 — Order/payment choreography saga
`OrderPlacedEvent` → payment charges (Stripe idempotency key = saga correlation id) →
`PaymentCompletedEvent` / `PaymentFailedEvent` → order status transitions; compensation
`OrderCancelledEvent` → refund; notification listens for emails. The synchronous
create-charge/refund REST calls in order are removed. Saga flow documented in
`docs/sagas/`; a reconciliation job handles stuck sagas.

### Phase 4 — gRPC internal reads
New `contracts` Maven module (`profile/v1`, `catalog/v1`, `payment/v1` protos,
protobuf-maven-plugin codegen, versions pinned in the root BOM). Spring gRPC servers in
profile (folding the WebMVC rework and dropping card DTOs), catalog (batch product
lookup), payment (get-customer). gRPC clients in order and profile. Deadline on every
call plus per-target Resilience4j CB/bulkhead around blocking stubs; identity via
metadata (`x-user-id` propagation); addresses via config (`localhost:<port>` in compose,
K8s headless-service DNS later). REST read endpoints stay during transition, removed
after cutover.

### Phase 5 — Platform/ops
Extract `common-web` (BusinessException hierarchy, problem+json advice,
MdcRequestFilter, IdempotencyInterceptor) and `common-events`; Dockerfiles for
notification/payment/profile; CI for all modules; `application-prod.yml`; K8s
manifests; Avro/Schema Registry (ADR-002) once `common-events` exists; outbox relay
batching.

**Ordering rationale:** Phase 2 precedes 3 because the saga depends on DLT/outbox/
idempotency. gRPC (4) follows the saga so the payment write path is already async and
gRPC carries only reads.

## Out of scope (explicitly deferred)

- Avro + Schema Registry (until `common-events` exists — Phase 5+)
- gRPC at the gateway edge / grpc-web
- Service mesh; keep plain client-side config until K8s manifests exist
