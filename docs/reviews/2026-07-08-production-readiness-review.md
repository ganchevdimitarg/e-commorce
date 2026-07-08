# Production Readiness Review — `com.ganchevdimitarg:e-commerce`

**Date:** 2026-07-08 · **Branch reviewed:** `main` (working tree) · **Reviewer role:** Senior Staff Engineer
**Scope:** all 10 modules. Note: the review prompt referenced Java 21 / Boot 3.x; this repo targets **Java 25 / Spring Boot 4.1.0**, so recommendations follow the repo's own (newer) target stack.

> **The single most important fact about this codebase:** it is *bimodal*. `catalog` and
> `authentication` are near-reference-quality Spring Boot 4 services. `order`, `payment`,
> `notification`, and `gateway` are legacy pre-migration code — and **`main` does not
> compile end-to-end** (`payment` imports `com.concordeu.catalog.*` classes that no longer
> exist; `gateway/pom.xml` still pins Spring WebFlux 5.3.9 and ships springfox alongside
> springdoc 1.6.x). The remediated versions of payment/gateway/order exist on the unmerged
> branch `feat/order-boot4-migration`. Every average score below hides this split.

---

## Severity-grouped findings (per `/review` skill)

### Critical
| # | Finding | Evidence |
|---|---|---|
| C1 | **`main` does not build.** `payment` imports non-existent `com.concordeu.catalog.*` (`payment/.../idempotency/IdempotencyInterceptor.java:3`, `config/ResourceServerConfig.java:3-5`, `repository/*.java`); `gateway/pom.xml:16` pins `spring-webflux 5.3.9`, ships springfox 3.0.0 + springdoc 1.6.x together, uses the retired `spring-cloud-starter-gateway` artifact. The fixed code sits unmerged on `feat/order-boot4-migration`. | payment, gateway |
| C2 | **Order creation is non-atomic and charges before persisting.** `OrderServiceImpl.createOrder` (`order/.../OrderServiceImpl.java:49-96`) has no `@Transactional`; it calls `chargeService.makePayment(...)` (real money) *before* `orderDao.saveAndFlush`, then saves items and the charge in separate flushes. A failure after payment → customer charged, no order, no compensation, no idempotency key on retry → **double charge**. | order |
| C3 | **The payment request body is garbage.** `ChargeServiceImpl.chargeCustomer` (`order/.../ChargeServiceImpl.java:56-64`) passes `PaymentDto.builder()...` to `Gson.toJson(...)` **without `.build()`** — it serialises the *Builder object*, not the DTO. The charge call cannot ever have worked as written. | order |
| C4 | **Money arithmetic is wrong.** `OrderServiceImpl.java:71-77`: `prices.reduce(...).toString().replace(".", "")` → `Long.parseLong`. `10.5` becomes `105` (not `1050` cents); scale-dependent; **item quantity is ignored entirely** — a customer ordering 10 units is charged for 1. |
| C5 | **Duplicate order numbers under concurrency/scale.** `OrderServiceImpl.java:35-40,84`: `orderCounter` is a plain `long` field seeded from `orderDao.count()` at startup and bumped with `++orderCounter` — not thread-safe, and guaranteed collisions with >1 instance (unique constraint turns them into 500s). |
| C6 | **IDOR + hard delete.** `OrderController.deleteOrder` (`order/.../OrderController.java:51-55`) → `orderDao.deleteByOrderNumber` with **no ownership check** — any user with `SCOPE_order.write` deletes anyone's order, permanently (hard delete; repo convention mandates soft delete). |
| C7 | **Committed keystore + hardcoded OAuth2 secret.** `gateway/src/main/resources/bootsecurity.p12` is in git; `client-secret: secret` appears in `catalog/application-dev.yml:133`, `order/application-dev.yml:107`, `notification/application-dev.yml:103`, `payment/application.yml:88`. |
| C8 | **Card PAN/CVC flow through order & profile as plain DTO fields.** `OrderServiceImpl.createProfileUser` (`:196-210`) forwards `cardNumber/cardExpMonth/cardExpYear/cardCvc` (plus a hardcoded password `"opaque"`) over REST to profile. PCI-DSS scope explosion; CVC must never be stored or relayed like this — only the payment service may touch card data (tokenised). |
| C9 | **Order Flyway history mutilated.** Only `V1__init_tables.sql` and `V6__drop_items_product_id_unique.sql` exist — V2–V5 were deleted from source. Any environment that applied V2–V5 now fails `flyway:validate`; a fresh environment silently skips four schema changes. Repo rule: never edit/remove a committed migration. |
| C10 | **Notification consumer can silently lose messages.** `KafkaListenerService.java:17-21` throws `JsonProcessingException` with no `@RetryableTopic`/DLT (only `profile` has DLT config); topic `sentMail` and group `notification` also violate naming (`<domain>.<entity>.<event>`, `<service>-group`). |

### Warning (selection — recurring themes)
- **Zero tests in gateway, order, payment, notification** (0 test files each vs catalog 32, auth 19, profile 9). The two money-path services are the least tested.
- **Legacy duplicate security config**: `gateway/src/main/java/com/concordeu/gateway/config/SecurityConfig.java` (permit-nothing chain, no `@Configuration`) still present alongside the real one.
- **Documented identity contract not implemented on `main`**: root CLAUDE.md says gateway injects trusted `X-User-Id`/`X-User-Roles`; no such filter exists on `main` (added only on the branch). Downstream services instead each re-introspect opaque tokens.
- **`CustomOpaqueTokenIntrospector` routes by token prefix** (`"ya29."`, `"gho_"`, else Facebook — `client/.../CustomOpaqueTokenIntrospector.java:18-26`): brittle if-ladder on a security-critical path; provider principals are `new`-ed per call; only catalog wraps it in a resilient decorator.
- **Sentinel-value error flow in order**: circuit-breaker fallbacks return DTOs with `""` fields, then `checkAvailabilityOfCatalogService(list.get(0).name())` (`OrderServiceImpl.java:159-176`) turns an outage into a *400 “check the request details”* — and `get(0)` on an empty list throws `IndexOutOfBoundsException`. `assert responseDtoList != null` is a no-op in production JVMs.
- **Reactive types outside gateway**: order services are built on `WebClient` + `.block()` (`OrderServiceImpl.java:162`, `ChargeServiceImpl.java:87,108`); profile is fully reactive (`Mono` in controller/service/DAO) while its own CLAUDE.md claims WebMVC — code/doc contradiction either way.
- **`excaption` (sic) exception packages** in order, notification, and payment (payment has *both* `exception/` and `excaption/` with duplicate `ControllerExceptionHandler`s); `@Data ErrorMessage` classes instead of the RFC 9457 problem+json contract that auth/catalog implement.
- **Order entity/DTO leakage**: `OrderDto` embeds JPA `Item` entities (`OrderServiceImpl.java:90-92` mutates them post-request); `Order` uses `String` UUID PK, `CascadeType.ALL`, no audit columns, no `@Version`, `LocalDateTime` over `Instant`/`TIMESTAMPTZ` (`V1__init_tables.sql`).
- **Catalog N+1 on page reads**: `convertProduct` (`ProductServiceImpl.java:179-189`) touches lazy `category` and `comments` per row inside `Page.map` with no `@EntityGraph`/fetch join anywhere in the module → 1+2N queries per 20-item page.
- **No `@CircuitBreaker`/`@Bulkhead`/`@TimeLimiter` in any business service** (grep: only `gateway/config/Resilience4jConfig.java`); order's `ReactiveCircuitBreakerFactory` usage misses bulkhead/timeout, and its clients send no `Idempotency-Key` (payment's interceptor had to be made optional because of this).
- **MDC/observability gap**: MdcRequestFilter exists in auth/catalog/payment only; order and notification have no MDC filter, no JSON logging, no metrics.
- **Doc drift**: `docs/decisions.md` referenced by root CLAUDE.md is missing; order/notification CLAUDE.md still say "does NOT compile" (stale); profile CLAUDE.md claims a duplicate `com.concordeu.profile.ProfileApplication` that no longer exists.
- **Shared `client` library owns state**: `client` ships a Mongo `OpaqueTokenDao` + AOP aspect used by multiple services — a library with its own datastore, quietly violating "each service owns its schema".
- **`OutboxRelay.publishOne`** blocks per-record on `kafkaTemplate.send(record).get()` (`OutboxRelay.java:62`) — serial sends; fine at current volume, a throughput ceiling later.

### Suggestion (selection)
- Order REST shape: `/create-order`, `/get-order`, `/delete-order` verbs + `@RequestParam` for resource ids; `void` 200 responses; `@PreAuthorize` on controllers (convention: service layer — catalog does this correctly).
- Custom `@ValidationRequest` AOP aspect in order/notification reinvents Bean Validation — replace with `@Valid` + constraints on records.
- `@Value` field injection on services (`OrderServiceImpl.java:42-47`) — move to a `@ConfigurationProperties` record (profile's `PaymentServiceProperties` is the in-repo example to copy).
- Notification's `static final ObjectMapper` (`KafkaListenerService.java:14`) bypasses the global `JacksonConfig`.
- Grammar/telegraphese log lines ("Items was successfully created", "Order was successfully delete") logged *before* outcomes are actually known.

---

## Category scores (1–10)

| # | Category | Score | Rationale (evidence above) |
|---|---|---|---|
| 1 | Overall architecture | **5** | Target architecture (edge gateway, DB-per-service, events, outbox) is sound and *documented*; implementation diverges: identity contract missing on main, order does a synchronous REST write-chain (order→profile→payment) with card data instead of the documented event-driven flow; sagas exist only as docs. |
| 2 | Package/module organization | **6** | Consistent `<service>.<layer>` layout in mature modules; ruined elsewhere by `excaption` packages ×3, payment's catalog cruft (`repository/Product*`, `dto/product/*`), and the live `com.concordeu` remnant in gateway. |
| 3 | SOLID | **5** | catalog services are single-purpose; `OrderServiceImpl` mixes authz, HTTP fan-out, pricing math, persistence and sentinel error handling (SRP); introspector if-ladder violates OCP; interfaces are mostly honest 1:1 seams. |
| 4 | Design patterns | **5** | Genuine wins: transactional Outbox (auth), `AbstractRoutingDataSource` read-replica routing (catalog), after-commit event publishing. Misuses: Builder-without-`build()` (C3), AOP-based request validation, missing Strategy for OAuth providers. |
| 5 | Clean Code | **5** | auth/catalog are javadoc'd and intention-revealing; order/notification carry misspelt packages, sentinel strings, `checkAvailabilityOfCatalogService(String token)` (neither availability nor token), dead `assert`s. |
| 6 | Object-oriented design | **5** | Anaemic-but-appropriate CRUD domains; catalog's `Product` (optimistic `@Version`, `@SQLDelete`) is well-formed; order's `Order` (String PK, `CascadeType.ALL`, mutable everything, entities inside request DTOs) is not. |
| 7 | Separation of concerns | **5** | Controller→service→repo held in mature modules; order's controller triggers email post-service, its service does inter-service orchestration + pricing; card data crosses three services. |
| 8 | Spring Boot best practices | **5** | Mature modules: Boot 4.1, `@ServiceConnection` Testcontainers, virtual threads, problem+json. Lagging: gateway's springfox+springdoc 1.x poms, order's legacy `bootstrap.yml`, static ObjectMapper, `.block()` on WebClient in WebMVC. |
| 9 | Dependency injection | **6** | Constructor injection + `@RequiredArgsConstructor` is the norm, no `@Autowired` fields found; deductions for `@Value` field injection in order services and `new`-ed principals in the introspector. |
| 10 | Configuration management | **5** | Vault + `${ENV_VAR}` and env-driven docker-compose are right; but `client-secret: secret` ×4, committed `.p12`, no prod profiles, missing `docs/decisions.md`, per-module config drift (bootstrap.yml vs spring.config.import). |
| 11 | Exception handling | **5** | Two regimes: auth/catalog `BusinessException` hierarchy → RFC 9457 advice (exemplary); order/notification/profile `InvalidRequestDataException` + `@Data ErrorMessage`, exceptions for control flow, and outage-as-400 sentinel logic (C-adjacent). |
| 12 | Validation | **5** | auth: constraints on records, compact constructors, custom `@StrongPassword` — textbook. order/notification: no `@Valid`, homegrown AOP validation, `IllegalArgumentException` for authz. |
| 13 | Security | **4** | Real OAuth2 authorization server, scope-based authz, outbox token redaction (+); committed keystore, hardcoded client secrets, PAN/CVC over internal REST, prefix-string token routing, permit-nothing zombie SecurityConfig, IDOR delete (−). |
| 14 | AuthN & authorization | **5** | End-to-end OAuth2 with JWT+opaque dual support (catalog's `AuthenticationManagerResolver`) is above-average; ownership checks by string compare in order, `@PreAuthorize` placement inconsistent, deleteOrder unchecked (C6). |
| 15 | API design (REST) | **4** | catalog/auth: versioned nouns, `PageResponse`, 201s. order: verb URLs, `@RequestParam` ids, `void` 200s, Swagger annotations documenting only 200/401/403/500. Cross-service inconsistency is itself the defect — clients can't learn one dialect. |
| 16 | DTO / entity separation | **5** | Records + MapStruct in catalog/auth/notification (+); JPA `Item` entities inside `OrderDto`, payment's on-main `PaymentDto` god-DTO, gateway `@Data ErrorResponse` (−). |
| 17 | JPA / Hibernate usage | **5** | catalog: optimistic locking, guarded `updateById`, soft delete via `@SQLDelete` (+). catalog N+1 on pages; order: `saveAndFlush` reflex, cascade-ALL, no version, String PKs (−). |
| 18 | Transaction management | **4** | catalog's `@Transactional` + `publishAfterCommit` and auth's outbox writer are exactly right; order's money path has **no transaction at all** (C2); `getProductsById` documents-away a real self-invocation gap. |
| 19 | Database design | **4** | auth (7 migrations) and catalog (8) have audit columns, partial indexes, triggers; order deleted V2–V5 (C9), lacks audit columns, uses `timestamp` w/o TZ and varchar ids; notification/payment have a single V1 each. |
| 20 | Performance & efficiency | **4** | Virtual-thread fan-out util and read-replica routing (+); per-request `.block()` chains in order, catalog page N+1, `orderDao.count()` at boot, no outbox batching, no connection-pool tuning evidence (−). |
| 21 | Caching opportunities | **5** | catalog: Redis product cache w/ TTL+eviction, idempotency keys — disciplined; nothing caches profile's per-read payment enrichment call or introspection results (client DAO exists but only catalog wraps it resiliently). |
| 22 | Concurrency & thread safety | **3** | `++orderCounter` on a shared field (C5) in the order hot path; `.block()` inside servlet threads; mitigation patterns (ScopedValue, StructuredTaskScope) documented but only `VirtualThreads.mapParallel` exists. |
| 23 | Logging & observability | **5** | auth/catalog: MDC filters, OTel/OTLP, Prometheus counters, JSON logs (+); order/notification: none of it, log-before-outcome, plaintext (−). |
| 24 | Testing | **4** | catalog: 136 tests, JaCoCo 85%/100% service, Testcontainers hierarchy, singleton PG container — genuinely good. auth: 19 test classes incl. HTTP/idempotency. profile: 9. **gateway/order/payment/notification: zero.** Failsafe unbound repo-wide → `*IT` never ran in-gate (first real context boot of payment crashed — known gotcha). |
| 25 | Error handling (runtime) | **4** | Fallbacks that fabricate empty DTOs convert outages into user-blaming 400s; disabled-by-default `assert` guards; notification throws on the listener thread with no DLT (C10). |
| 26 | Code readability | **6** | The good half reads like documentation (OutboxRelay's comments explain *why*); the bad half misspells its own package names. |
| 27 | Maintainability | **4** | Main doesn't build; a large finished remediation is stranded on an unmerged branch; six copies of the exception hierarchy + idempotency interceptor + MDC filter (no `common` module); stale module CLAUDE.md notes mislead the next engineer. |
| 28 | Scalability | **4** | Stateless services + Kafka + read replica (+); order cannot run 2 instances (C5), sync order→profile→payment chain couples availability, no rate limiting on main, outbox relay is serial (−). |
| 29 | Documentation | **7** | Best-in-class engineering docs: layered CLAUDE.md system, 4 ADRs, pattern library, runbook, saga templates, phase plans. Docked for drift: missing `decisions.md`, stale compile notes, docs describing unimplemented contracts as current. |
| 30 | Production readiness | **3** | Won't compile on main; untested and defective money paths; secret hygiene failures; 5 services lack Dockerfiles; CI covers catalog only; no DLT. Not deployable. |

---

## Architecture-style assessment

- **Layered architecture**: Yes, consistently controller→service→repository; direction respected in mature modules, breached in order (entities in DTOs). This is the right style for these services — do not add hexagonal ports/adapters ceremony; the payment branch's `PaymentGateway` strategy already shows the correct, minimal port where it pays off (confining the Stripe SDK).
- **Clean/Hexagonal**: Not followed, and **not recommended** wholesale — YAGNI. Adopt only the "confine vendor SDKs behind one interface" rule.
- **DDD**: Light-touch: good aggregate discipline in catalog (Product/Category/Comment), none in order (Charge/Order/Item boundaries leak). Full tactical DDD unwarranted for CRUD-heavy services.
- **CQRS**: Only catalog's read-replica routing (a reasonable, minimal CQRS-lite). Sufficient.
- **Event-driven**: Half-real. auth→profile via outbox + consumer with DLT is production-grade choreography; catalog publishes after-commit correctly; order/notification still use point-to-point sync REST + a misnamed ad-hoc topic. The documented saga (order compensation) is not implemented on main.

**Code smells inventory**: god method (`createOrder`), sentinel values, message chains (`order.get().getItems().stream()...`), primitive obsession (money as `long`/`String` manipulation), duplicated exception/idempotency/MDC code ×6 modules (DRY), speculative docs for unbuilt infra (inverse-YAGNI in docs only), dead code (`com.concordeu` gateway config, payment's catalog repositories), typo packages, stale comments (CLAUDE.md compile notes), library-with-a-database (`client`), field injection, control-flow-by-exception, N+1, missing pattern (Strategy for OAuth providers), misused pattern (Builder C3, AOP validation).

---

## Overall Score

**5 / 10.** Weighted for risk, not averaged: the money-handling half of the system (order, payment on main) is broken at the compile, correctness, security, and test level simultaneously, and that outweighs the excellence of catalog/authentication. The same team demonstrably knows how to build this correctly — the gap is *convergence and integration*, not skill.

**`/review` skill scorecard — repo audit**

| Dimension | Score | Justification |
|---|---|---|
| Correctness & Safety | 3/10 | Critical cap: C1–C10 (non-compiling main, non-atomic charging, builder-serialisation bug, money arithmetic, counter race) |
| Convention compliance | 5/10 | catalog/auth near-full compliance; order/notification/payment/gateway violate Lombok, Kafka naming, soft-delete, idempotency, problem+json rules en masse |
| Design & SOLID | 5/10 | Clean seams in mature modules vs `OrderServiceImpl` mixed concerns and OCP-violating introspector |
| Design patterns | 6/10 | Outbox, routing datasource, after-commit publisher earn their keep; Builder misuse and AOP-validation cargo cult deduct |
| Readability & maintainability | 5/10 | Bimodal: javadoc'd intent vs `excaption`/sentinel spaghetti; unmerged-branch divergence is the maintainability killer |
| Testability & tests | 4/10 | catalog exemplary (136 tests, 85%), but 4 of 10 modules — including both money services — have zero tests and failsafe is unbound |

**Overall: 45/100 — Grade D** (Critical findings cap correctness at 3 and the grade below C regardless of the strong modules.)

## Production Readiness

**Rejected.** I would not approve this for production, on five independent grounds, any one of which is disqualifying for a high-traffic system: (1) `main` does not compile; (2) the order→payment flow can double-charge, mis-charge (quantity ignored, decimal-shift bug), and charge without an order; (3) IDOR on order deletion and card data traversing three services; (4) committed keystore and hardcoded OAuth secrets; (5) zero automated tests on the two services that touch money. catalog and authentication individually are close to approvable.

## Top 10 Critical Issues (by severity)

1. **C2+C4+C3 — the order money path is wrong end-to-end**: charges before persisting without a transaction, with corrupted amounts, via a request body that serialises a Lombok Builder. Financial-loss class.
2. **C1 — main branch does not build** (payment `com.concordeu` imports; gateway pre-Boot-4 pom). Everything else is unverifiable until this lands.
3. **Unmerged remediation branch** — `feat/order-boot4-migration` already fixes much of 1–2 (15 payment commits, gateway identity filter, tests). The divergence itself is the risk: work is being redone and reviewed against dead code.
4. **C8 — PAN/CVC + hardcoded password `"opaque"` in inter-service DTOs** (PCI).
5. **C6 — IDOR hard-delete of any order.**
6. **C7 — committed `bootsecurity.p12` + `client-secret: secret` ×4** (rotate, purge history).
7. **C5 — in-memory order-number counter** blocks horizontal scaling and races.
8. **C9 — deleted Flyway versions V2–V5 in order.**
9. **Zero tests + unbound failsafe in gateway/order/payment/notification**; payment's known latent reactive-autoconfig boot crash likely repeats in order/catalog ITs.
10. **C10 — notification consumer without retry/DLT + non-convention topic/group.**

## Quick Wins (< 1 day each)

1. Merge (or at minimum cherry-pick the payment/gateway fixes from) `feat/order-boot4-migration` — most Criticals close instantly.
2. `ChargeServiceImpl.java:63`: add the missing `.build()`; replace Gson with the injected Jackson mapper and pass the object to `bodyValue()`.
3. Wrap `createOrder` in `@Transactional` and reorder: validate → price → persist order+items → charge → record charge (still not a saga, but no more orphan charges).
4. Add ownership check to `deleteOrder` and switch to soft delete.
5. Delete `gateway/src/main/java/com/concordeu/**` and `gateway/src/main/resources/bootsecurity.p12` (rotate the cert; `git filter-repo` the history); replace the four `client-secret: secret` literals with `${...}` env refs.
6. Replace `orderCounter` with a Postgres sequence (`V7__order_number_seq.sql` + `@GeneratedValue`-backed or `nextval` query).
7. Rename `excaption` → `exception` in order/notification; delete payment's duplicate `excaption/` handler and catalog-cruft files (branch already did this).
8. Rename topic `sentMail` → `notification.email.send`, group → `notification-group`; add `@RetryableTopic` + DLT to the listener.
9. Bind `maven-failsafe` in every module pom that has `*IT` classes (known one-line-per-pom fix, with the `ReactiveOAuth2ResourceServerAutoConfiguration` exclusion where needed).
10. Restore/reconcile order migrations V2–V5; add `V__add_audit_columns` for order/notification.
11. Fix stale module CLAUDE.md notes and create the referenced `docs/decisions.md`.

## Long-Term Refactoring Plan (priority order)

1. **Converge on one branch** and make the root reactor + full CI (test/build per module, image `<service>:<git-sha>`) the merge gate — the repo's biggest risk is parallel truths, not code.
2. **Extract `common-web` + `common-events` modules**: the `BusinessException` hierarchy, problem+json advice, `IdempotencyInterceptor`, `MdcRequestFilter`, and event records are hand-copied across up to 6 modules today. (Keep it thin; resist a `common` kitchen sink. Strip the Mongo DAO out of `client` — a shared library must not own a datastore.)
3. **Rewrite the order money path** as the documented choreography saga: money as `BigDecimal`/minor-units record, per-attempt `Idempotency-Key` to payment, outbox-published `OrderPlacedEvent`, compensation via payment's existing full-refund endpoint. Reuse auth's `OutboxRelay`/`OutboxWriter` as the template.
4. **Implement the gateway identity contract on main** (`X-User-Id`/`X-User-Roles` injection + downstream strip) and delete per-service prefix-based re-introspection; replace `CustomOpaqueTokenIntrospector`'s if-ladder with a provider Strategy map, cached in Redis with TTL.
5. **Test the untested**: bring order/payment/notification to the catalog bar (Testcontainers base hierarchy exists; JaCoCo 80/100 gate) — money paths first, WireMock for inter-service stubs.
6. **Standardise HTTP clients**: `RestClient` + `@CircuitBreaker`/`@Bulkhead`/`@TimeLimiter` per the resilience defaults in every business service; retire WebClient/`.block()` outside gateway.
7. **Fix catalog page N+1** with `@EntityGraph`/fetch-join or a projection query; then Avro/Schema Registry (`common-events`) per ADR-002, replacing per-module JSON serialisers.
8. **Ops floor for every service**: Dockerfile (copy the authentication one — it is the best), JSON logging, MDC filter, prod profile, health/prometheus exposure; extend the Jenkins pipeline beyond catalog.

## Strengths

- **`authentication`'s transactional outbox** (`OutboxRelay`, `OutboxWriter`): correct at-least-once semantics, per-row failure isolation, trace headers, interrupt handling, and *deliberate redaction of reset tokens in published rows* — with comments explaining Jackson-2-vs-3 pitfalls. Staff-level work.
- **`catalog` as a reference module**: optimistic locking with guarded bulk update, `@SQLDelete` soft delete, after-commit event publishing, capped pagination (`PageableSupport`), metrics counters, dual JWT/opaque auth resolution, read-replica `AbstractRoutingDataSource` with health probe, 136 tests at 85% coverage, and a Jenkins pipeline.
- **The documentation system itself**: layered CLAUDE.md + on-demand pattern docs + ADRs + runbook is better than most production teams maintain — it just needs to stop describing the future in the present tense.
- **Testcontainers hierarchy** (`AbstractIntegrationTest` singleton-PG / `RedisKafkaIntegrationBase`) — the right shape, ready for the untested modules to adopt.
- **Dockerfile quality where they exist** (multi-stage, non-root, HEALTHCHECK, explicit jar name, CRLF guard in the authentication one).
- **Idempotency-Key infrastructure** in auth/catalog/payment with Redis-backed dedupe and 409 semantics.

---

## Addendum — remediation applied 2026-07-08 (same day)

After this review, `main` was fast-forwarded to `feat/order-boot4-migration` (63 commits)
and the residual quick wins applied. Status of the Top 10:

| Issue | Status |
|---|---|
| 1. Order money path (tx/idempotency/amount/builder bug) | **Fixed by merge** — charge outside tx with refund compensation, per-attempt idempotency keys, `OrderLineDto`, Gson removed; 57 order tests green |
| 2. `main` does not build | **Fixed** — root reactor `clean compile` green; gateway/notification/payment/order `clean verify` green |
| 3. Unmerged branch divergence | **Fixed** — fast-forward merge, no divergence |
| 4. PAN/CVC in DTOs | **Fixed by merge** for order (card fields gone from `OrderDto`); profile's card DTOs remain — follow-up |
| 5. IDOR hard-delete | **Fixed by merge** — ownership-checked cancel flow with soft delete + status history |
| 6. Keystore + hardcoded secrets | **Fixed at HEAD** — `.p12` deleted by merge; four `client-secret: secret` literals replaced with `${GATEWAY_CLIENT_SECRET}` (this session). **History still contains the .p12 — rotate the cert; purging history (`git filter-repo`) is a user decision.** |
| 7. In-memory order counter | **Fixed by merge** — `V5__order_number_sequence.sql` |
| 8. Deleted Flyway V2–V5 | **Fixed by merge** — V2–V5 recreated (audit columns, status, history, sequence) |
| 9. Zero tests / failsafe unbound | **Largely fixed** — gateway 4, payment 46+6 IT (JaCoCo gate), order 57; notification still untested (its migration branch is in flight) |
| 10. Notification DLT + naming | **Partially fixed** — topic `order.notification.requested`, group `notification-group`; `@RetryableTopic`/DLT deliberately deferred to `feat/notification-boot4-migration` |

Also done: root `docs/decisions.md` created; stale notification/profile CLAUDE.md notes
corrected. Post-merge score movement: correctness/transactions/concurrency/testing all
rise materially; the repo's grade is now gated by notification (untested), profile
(reactive deviation, card DTOs), the catalog N+1, and the missing `common` module.
