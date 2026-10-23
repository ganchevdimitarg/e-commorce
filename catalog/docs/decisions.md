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
- 2026-06-23 — Read-replica staleness policy accepted: browse reads routed to replica (mild lag acceptable); writes + Flyway always on primary; replica outage degrades to primary. Read-after-write is NOT guaranteed — Phase 5 cache evicts on write (not write-through), so an immediate re-read can hit a lagging replica. Gap documented; write-through cache recommended as short-term fix. See ADR-005.
- 2026-06-21 — The project-local `.claude/skills/brainstorming/` fork is canonical; `superpowers:brainstorming` is superseded in this repo. Its `description:` keeps the upstream activation trigger verbatim but appends a "Project-canonical fork" marker so the two skills no longer share byte-identical activation text. Alternatives rejected: keeping the description byte-identical (left activation non-deterministic — the plugin copy could fire instead of the 97/100 fork). Rationale: closes the dual-activation ambiguity flagged in the 2026-06-21 setup evaluation.
- 2026-10-20 — Catalog validates only auth-service JWTs; social opaque tokens terminate at auth/gateway. Rejected: copying `client`'s introspector into catalog. Rationale: gateway owns the identity contract (root decisions, 2026-07-05) and downstream services must not re-introspect; this also removes catalog's dependency on the shared `client` module.
- 2026-10-20 — Catalog derives request identity and the log `userId` from the JWT principal (`Authentication.getName()` / `SecurityContextHolder`), never from the gateway `X-User-Id` header. Rejected: trusting the header for comment authorship or MDC. Rationale: catalog is reachable directly on :8084, so a client-supplied header is spoofable; the JWT is validated locally. This narrows the root convention for catalog after 7.1.
- 2026-10-20 — Catalog idempotency keys are scoped by principal + method + path and store an IN_PROGRESS/COMPLETED record with response replay; Redis outage fails open. This supersedes the 2025-06 "never per-user" row. See ADR-006.
- 2026-10-20 — Catalog write-through: create/update populate the product cache after commit (ADR-005 option 1); delete and list reads remain eventually consistent.
- 2026-10-20 — Catalog product events use a transactional outbox and a scheduled relay (at-least-once, consumers dedupe on eventId). See ADR-007.
- 2026-10-20 — `PUT /api/v1/catalog/products/{id}` accepts `UpdateProductRequestDto` (no `name`) and returns `200` with the updated `ProductResponseDto`; a supplied `name` is ignored. Rejected: keeping the shared create DTO and 204 with no body. Rationale: the service already builds the updated DTO, and returning it removes a follow-up GET without breaking status-code checks.
- 2026-10-20 — Comment responses expose `id`, `productId` and `productName` instead of the nested product, creation returns `Location: /api/v1/catalog/comments/{id}`, and `DELETE /comments/{id}` is allowed for the author or a `ROLE_ADMIN`. Catalog preserves `ROLE_*` authorities from the token's `scope` claim while keeping the default `SCOPE_` mapping.
- 2026-10-21 — Catalog's JaCoCo bundle line gate is 90 % (measured 95.03 % on 2026-10-21 after the Phase 6 dead-code cleanup; the plan's 0.90 target is retained rather than the 95 % bucket, leaving headroom below the measured value) and `service.*` remains 100 %. `verify` already binds `jacoco:report`/`jacoco:check`, and the root Jenkinsfile already runs `./mvnw verify -pl catalog -am`; its "80 %" comment is stale and is left for TODO 7.6. Rejected: leaving the gate at 85 % with several points of unenforced headroom.
- 2026-10-21 — Catalog exposes liveness (`livenessState`, `ping`) and readiness (`readinessState`, `db`) groups. `ReadReplicaHealthIndicator` (bean `replica`) reuses the router's 2-second cached probe and reports `UP` with `reader`/`route` details when the replica is unreachable, because reads fall back to the writer. Redis and the replica are deliberately excluded from readiness; cache failures are misses and replica loss degrades to the writer.
- 2026-10-21 — Catalog is a JWT-only resource server; no opaque-token introspection or token cache exists. The TODO 6.4 "token caching" ADR is superseded by ADR-008 because 7.1 removed the introspector. See ADR-008.
- 2026-10-21 — Final Phase 6 coverage measurement after Task 4's health classes is 94.95 % (715/753 lines), complementing the 95.03 % pre-Task-4 line.
- 2026-10-20 — Catalog owns its runtime configuration: environment-backed properties, a `dev` profile with localhost defaults, a `prod` profile with no dev-only infrastructure and Swagger disabled, and a catalog-owned Vault path (`secret/catalog/credentials`). Rejected: keeping `auth-service` Vault paths and dev defaults in the base file. Rationale: Phase 7 extraction requires catalog to run without the monorepo's shared infrastructure.
- 2026-10-21 — Catalog ITs share one Postgres, one Redis and one Kafka container per JVM. `RedisIntegrationBase` and `RedisKafkaIntegrationBase` start their containers in static initialisers (not `@Container`), `ProductCacheIT`/`IdempotencyIT` no longer start private Redis instances, and `.withReuse(true)` plus `testcontainers.reuse.enable=true` keeps Redis and Kafka across local runs (Postgres stays a non-reusable singleton with a fresh container per run). The catalog Jenkinsfile passes `-Dtestcontainers.reuse.enable=false` because its agent disables Ryuk, so reusable containers would never be reaped and concurrent builds would share one Redis; the root Jenkinsfile still runs catalog ITs with reuse enabled until the root-pipeline cutover (outside `catalog/`). Testcontainers 2.0.5 reads `testcontainers.reuse.enable` only from `~/.testcontainers.properties` or the environment, never from the classpath file, so Surefire exports `TESTCONTAINERS_REUSE_ENABLE` from the `testcontainers.reuse.enable` Maven property; the repository `src/test/resources/testcontainers.properties` stays as the reproducible classpath settings file. Kafka isolation rests on unique consumer groups (`OutboxRelayIT` appends a UUID) and assertions keyed on per-test random ids, never on "the topic is empty". Rejected: per-class containers, which started up to three Redis instances and recreated Spring contexts.
- 2026-10-21 — Catalog audits `created_by`/`updated_by` from the JWT principal, falling back to `system` for scheduled/anonymous work. Columns are nullable because existing rows have no known author. JPA auditing cannot see bulk JPQL updates, so the three product bulk queries take an explicit `updatedBy`; outbox rows remain operational state and are not audited. Soft deletes are not audited: all three entities delete through Hibernate `@SQLDelete`, which runs raw SQL without triggering `@PreUpdate`, and Spring Data's `AuditingEntityListener` has no `@PreRemove` hook, so `updated_by` keeps the last modifier after a soft delete; adding `deleted_by` or an explicit soft-delete audit is a deferred follow-up.
- 2026-10-21 — Catalog intentionally hard-deletes soft-deleted rows older than the configured retention (default 90 days, opt-in `catalog.retention.purge-enabled=false`). The purge uses native SQL because `@SQLRestriction` hides soft-deleted rows, deletes comments before their products, and only removes categories with no remaining products. This is the sole exception to the “never hard-delete business data” rule; the retention window is the audit window.
- 2026-10-21 — Catalog stores `rating_count` and `avg_stars` on `products` and recalculates them synchronously in the comment transaction. Rejected: adding `CommentCreated`/`CommentDeleted` events and a catalog Kafka consumer to update its own rows — catalog owns both aggregates, there is no consumer, and self-consumption would add latency, duplicate handling and eventual consistency without a benefit. If another service needs comment events later, add them to the outbox and a consumer there. Accepted trade-off: two overlapping comment create/delete transactions for one product both write the product row, so the losing transaction fails its `@Version` predicate and the whole transaction — including the comment write — rolls back as `409 CONFLICT`; the client retries, and the version guard means no rating update is silently lost. Pessimistic locking was rejected: it would serialise comment writes per product for a derived value that self-heals on the next mutation. — Locking clause superseded 2026-10-22, see below.
- 2026-10-21 — Catalog rate-limits product search (offset and cursor) at 60/min and batch-get at 10/min per principal with Resilience4j core `RateLimiter` in a `HandlerInterceptor`, returning 429 problem+json with `Retry-After`. The gateway’s 20 rps/40 burst remains the edge limit; catalog is directly reachable on 8084, so this is defence in depth. Rejected: `resilience4j-spring-boot4`/AOP annotations, which add a Spring Boot module and proxy machinery for three methods.
- 2026-10-21 — Catalog dead-letters poisoned outbox events in the database (`dead_lettered_at`/`dead_letter_reason`) after classifying non-retryable Kafka errors; broker/network errors keep retrying. No `.DLT` topic is created because a payload that fails serialisation cannot be published. Consumer-side DLT remains the responsibility of the consuming modules. See ADR-009.
- 2026-10-21 — Catalog validates the four product event payloads against versioned JSON Schema files before writing the outbox row; an invalid payload fails the transaction. This is a producer contract, not a registry. Avro + Confluent Schema Registry remain deferred to `common-events` by ADR-002. See ADR-010.
- 2026-10-21 — Catalog publishes producer-side Spring Cloud Contract stubs for its REST API. The `order` module is the only in-repo consumer, but its configured path `/catalog/product/get-products-id` does not exist; the real contract is `POST /api/v1/catalog/products/:batch-get` (Spring combines the colon-style sub-path with the class-level `/api/v1/catalog/products`, so the slash before the colon is part of the registered path). Fixing order is a cross-module change outside this task. No in-repo module consumes catalog events, so event contracts are covered by the JSON Schema files (ADR-010), not by a consumer-driven broker.
- 2026-10-22 — Catalog mutation-tests `service.*` and `idempotency.*` through the opt-in `pitest` profile (PIT 1.30.1 + pitest-junit5-plugin 1.2.3, unit tests only, not bound to `verify` or any lifecycle phase; run manually from `catalog/` with `./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage`). Baseline score: 88 % (141/160 mutants killed, 12 survived, 7 no coverage); threshold: 85 %. Target tests are the unit-tagged `ProductServiceImplTest`, `CategoryServiceImplTest` and `CommentServiceImplTest` plus `idempotency.*Test`; no `*IT` class is selected, so PIT never starts Testcontainers. PIT 1.30.1 supersedes the plan's 1.16.1, which predates Java 25 and cannot read class-file major 69. The profile is manual until the score is stable in CI.
- 2026-10-22 — Supersedes the locking clause of the 2026-10-21 rating decision: `createComment` and `deleteComment` take a `PESSIMISTIC_WRITE` row lock on the product before recalculating the rating. Overlapping comment mutations now queue and both commit instead of the loser rolling back with `409 CONFLICT`; the product is still dirty-checked, so its `@Version` and ETag advance on every rating change. The accepted trade-off is serialised comment writes per product, replacing retry-on-conflict. Product edits keep their optimistic predicate, so a product update racing a comment mutation can still return 409. The comment→product association is lazy so the delete path can read the foreign key from the proxy and take the lock before loading the product; loading it eagerly first produced a stale managed version and an optimistic-lock failure on the locked read. Rejected: an atomic native update (bypasses Hibernate auditing and lifecycle for marginal round-trip savings) and comment events with a self-consumer (see the superseded entry).
- 2026-10-22 — Catalog's JSON Schema event contract is now version-dispatched and compatibility-checked in-repo. `EventSchemaCatalog` loads every `<topic>.v<N>.json`, rejects filename/`$id`/`schemaVersion.const` mismatches, duplicates, unknown topics and missing topics at startup; `EventSchemaValidator` selects the schema from the payload's `schemaVersion`; and `EventSchemaCompatibilityTest` enforces a BACKWARD policy between consecutive versions as part of `verify`. Subjects are registry-ready as `<topic>-value`. Rejected: adding Apicurio or Confluent now, because no in-repo module consumes catalog events and ADR-002 still defers the shared Avro infrastructure. See ADR-010.
- 2026-10-22 — Catalog splits `catalog.write` into `catalog.review` (comment create/delete) and
  `catalog.manage` (product/category create, update, delete and moves); `catalog.write` is removed,
  not aliased. The auth service owns the scope seed and must add the two new scopes to the gateway
  client and any API client; until it does, review-only and manage-only tokens are rejected as
  designed. Rationale: comment moderation no longer confers repricing or deletion rights.
- 2026-10-22 — Catalog reads roles from a dedicated `roles` claim (collection or
  space-delimited string); a value without the `ROLE_` prefix is prefixed once. `ROLE_*`
  values in the `scope` claim remain accepted through a deprecated compatibility path for
  existing tokens.
- 2026-10-22 — Catalog serves actuator `info` and `prometheus` only on the separate management
  port 8088 (`MANAGEMENT_PORT`), not the app port 8084; `/actuator/health/**` stays public.
  Boot applies the single application `SecurityFilterChain` to the separate management server,
  so `ResourceServerConfig` permits requests arriving on the management port while the app port
  still rejects info and prometheus. The management port is unauthenticated by design and must
  be reachable only from the monitoring network. Rejected: 8085 (owned by notification) and
  app-port token protection (every Prometheus scraper would need a JWT). If app-level
  protection on 8088 is required, add a `ManagementContextConfiguration` with its own
  `SecurityFilterChain`.
- 2026-10-22 — Catalog's Redis `product` cache uses the typed
  `JacksonJsonRedisSerializer<ProductResponseDto>` (Jackson 3), not the polymorphic
  `GenericJackson2JsonRedisSerializer`; values carry no `@class` type metadata. The prefix is
  `catalog:v4:` so v3 entries are never deserialized as the typed DTO. Prod Redis auth/TLS is
  environment-backed (`REDIS_PASSWORD`, `REDIS_SSL_ENABLED`). Rejected: Jackson 2's typed
  serializer, which is deprecated for removal in Spring Data Redis 4.1.
- 2026-10-22 — Catalog product responses no longer embed the comment collection:
  `ProductResponseDto.comments` and `Product.comments` are removed; `avgStars` and
  `ratingCount` carry the summary and `GET /products/{name}/comments` serves the
  paginated reviews. The cache prefix moves to `catalog:v5:` so v4 entries containing a
  `comments` property are never bound to the new record. Rejected: a capped comments
  array (still unbounded per product and duplicates the paginated endpoint).
- 2026-10-22 — Catalog indexes product-name search with a `pg_trgm` GIN index on
  `lower(name)` (`idx_products_name_trgm`, partial `WHERE deleted_at IS NULL`) so the
  contains predicate can be index-scanned. `pg_trgm` is a deployment prerequisite;
  PostgreSQL 13+ treats it as trusted, but a restricted database user may need a DBA to
  create the extension once. Rejected: a B-tree index (cannot serve a leading-wildcard
  LIKE), full-text search (changes match semantics from substring to lexeme), and an
  in-memory filter (unbounded read). Two limits are accepted:
  (a) `V20` uses a non-concurrent `CREATE INDEX`, so Flyway's startup migration holds a
  `SHARE` lock on `products` for the GIN build and briefly blocks writes from older
  instances during a rolling deploy; this is accepted for the current catalogue size, and
  a future growth point should switch to a non-transactional `CREATE INDEX CONCURRENTLY`
  migration plus a manual repair step for an invalid index, because `IF NOT EXISTS`
  would skip the retry;
  (b) `ProductNameTrigramIndexIT` inlines the pattern and so proves index usability, not
  the runtime plan of the bound-parameter query; pgjdbc prepares statements after five
  executions and PostgreSQL may then choose a generic plan that cannot use a trigram
  index, so if measurements show search falling back to a scan, set
  `plan_cache_mode=force_custom_plan` (or pgjdbc `prepareThreshold=0`) on the catalog
  datasources.
- 2026-10-22 — `moveAllProducts` is bounded by `catalog.category.move-all-max` (default
  5,000). Above the cap it returns 409 `CONFLICT` and changes nothing; the move stays one
  transaction, loads only the id, name, price, stock and version the `category-changed`
  event needs, and writes the outbox rows with `saveAll`. Rejected: chunked processing
  with a transaction per chunk, which loses all-or-nothing and leaves a partially moved
  category after a failure.
  The cap is re-checked against the projected rows, so a concurrent insert between the
  count and the projection cannot push the move past it. A product inserted after the
  projection is still moved by the bulk update without an event, and a concurrent update
  in that window leaves the event's version one behind — both pre-existing races accepted
  for the current catalogue size. `saveAll` is used per the task's prescription; without
  `hibernate.jdbc.batch_size` it is not a JDBC batch, so a full-cap move issues one insert
  per row inside the single transaction.
- 2026-10-23 — The retention job purges delivered outbox rows after
  `catalog.outbox.retention` (default 7 days) using a partial index on `sent_at`.
  Unsent rows and dead-lettered rows are never selected; the latter stay for inspection
  and replay. The outbox window is independent of the 90-day soft-delete window, and the
  purge only runs when `catalog.retention.purge-enabled=true`. Rejected: an unbounded
  `delete` (long lock and a huge transaction), and deleting all rows older than the
  window regardless of state (would discard undelivered events). `V21` builds the index
  non-concurrently, so the startup migration briefly holds a `SHARE` lock on
  `outbox_events` and blocks inserts; this is the same trade-off accepted for `V20`, and
  the same growth point should switch both to `CREATE INDEX CONCURRENTLY`.
