# Catalog Service — Improvement To-Do (ordered by priority)

Scope: `catalog/` only. Base package: `catalog/src/main/java/com/ganchevdimitarg/catalog`.
Effort: **S** < 30 min · **M** 1–2 h · **L** half a day+. One task = one Claude Code session = one commit.

---

## How to run this list with Claude Code

**Session prompt (paste, replace `{ID}`):**

> Read `catalog/TODO.md` and implement task **{ID}** only. Rules: stay inside `catalog/` (do not edit other modules); write a failing test first, then the fix; never edit an existing Flyway migration, add a new one (next free version, currently V11); follow existing conventions (record commands, problem+json errors, `should_x_when_y` test names). Run the verify command from task 0.1, show me the result, then commit with the message given in the task. If anything is ambiguous, ask before coding.

**Tips**
- Use **plan mode** for every **L** task and for 4.4, 7.1. Review the plan before letting it edit.
- `/clear` between tasks so each one starts with clean context.
- Integration tests need **Docker running** (Testcontainers).
- Tick the box here when a task is merged, so the next session sees the current state.

---

## Phase 0 — Setup (do first, 20 min)

- [x] **0.1 Baseline (S).** Create branch `catalog-hardening`. Find out how tests run (`catalog/pom.xml`: surefire/failsafe, the `integration` tag) and run everything. Record the exact **unit** and **full verify** commands plus pass/fail counts at the top of `catalog/CLAUDE.md`.
  *Done when:* commands are documented and the baseline is green (or failures are listed). *Commit:* `chore(catalog): record test baseline and commands`
- [x] **0.2 Read existing guidance (S).** The repo root already has `CLAUDE.md`, `.claude/`, and `decisions.md` (not reviewed in this analysis). Read them; if catalog-specific rules are missing, add a short `catalog/CLAUDE.md` section: don't touch other modules, Flyway rules, test naming, problem+json errors.
  *Commit:* `docs(catalog): add catalog-specific Claude guidance`

---

## Phase 1 — Correctness bugs (fix before anything else)

- [ ] **1.1 Error handling: 403 and DB conflicts (S).**
  *Why:* `@ExceptionHandler(Exception.class)` in `ControllerExceptionHandler` very likely swallows `AccessDeniedException` from `@PreAuthorize`, returning 500 instead of 403. There is also no handler for `DataIntegrityViolationException`.
  *Do:* write an MVC test with method security enabled that calls a write endpoint with only `SCOPE_catalog.read` and expects 403 problem+json (confirm it fails first). Fix by rethrowing `AccessDeniedException`/`AuthenticationException` from the catch-all (or add explicit handlers). Add a `DataIntegrityViolationException` handler → 409 `CONFLICT`.
  *Files:* `exception/ControllerExceptionHandler.java`, `exception/ControllerExceptionHandlerTest`, a new MVC test.
  *Done when:* 403 test passes and a duplicate-key violation returns 409. *Commit:* `fix(catalog): return 403 for denied access and 409 for integrity violations`
- [ ] **1.2 Re-creating a soft-deleted name (M).**
  *Why:* the unique constraints on `products.name` and `categories.name` still include soft-deleted rows, but `findByName` hides them, so re-creating a deleted name passes the check and fails in the database.
  *Do:* first inspect real constraint names (`\d products`, `pg_constraint`; V1 created an auto-named unique on `categories.name`). New migration `V9`: drop those unique constraints and the duplicate name indexes, and create **partial unique indexes** `... (name) WHERE deleted_at IS NULL` for products and categories. Update `@UniqueConstraint`/`unique = true` annotations on the entities. Test: create → delete → create same name succeeds; two live duplicates → 409.
  *Files:* `db/migration/V9__*.sql`, `domain/Product.java`, `domain/Category.java`, `persistence/CatalogPersistenceIT`.
  *Commit:* `fix(catalog): make name uniqueness apply only to non-deleted rows`
- [ ] **1.3 Category delete must not cascade silently (S).**
  *Why:* `Category.products` has `CascadeType.ALL`, so deleting a category soft-deletes every product with no events and no cache eviction.
  *Do:* remove `CascadeType.ALL` (use none). In `CategoryServiceImpl.deleteCategory` throw `ConflictException` if the category still has products (tell the caller to use the move endpoints first). Test both paths.
  *Commit:* `fix(catalog): refuse to delete categories that still contain products`
- [ ] **1.4 Cache invalidation gaps (M).**
  *Why:* `ProductResponseDto` embeds category and comments, but only update/delete evict `product::{id}`. Creating a comment or moving a product serves stale data for up to 10 minutes.
  *Do:* evict on `createComment` (key = product id), `moveOneProduct`, and `moveAllProducts`/`deleteCategory` (use `allEntries = true` on the `product` cache where ids aren't known). Extend `cache/ProductCacheIT` with one test per case.
  *Commit:* `fix(catalog): evict product cache on comment and category changes`
- [ ] **1.5 Batch-get: bound it and use one query (M).**
  *Why:* `POST :batch-get` has no size limit and starts one virtual thread and one DB connection per id (writer pool 10, reader pool 20).
  *Do:* add `@Size(max = 100)` to `ItemRequestDto.items`; replace the `VirtualThreads.mapParallel` fan-out with a single `findAllById` (with category entity graph), keep input order, and throw `NotFoundException` listing **all** missing ids. Keep `VirtualThreads` only if something else uses it (see 6.2).
  *Done when:* a test with 101 ids → 400 and with one missing id → 404 naming it. *Commit:* `fix(catalog): bound batch-get and load products in one query`
- [ ] **1.6 Average stars + star validation (S).**
  *Why:* `getAvgStars` loads every comment into memory; `star` is an unvalidated `double`.
  *Do:* add a repository query `select avg(c.star) from Comment c where c.product.name = :name` (null → 0.0). Add `@DecimalMin("1") @DecimalMax("5")` (or switch to int 1–5) to `CommentRequestDto` and `CreateCommentCommand`. Add migration `V10` with `CHECK (star BETWEEN 1 AND 5)`; check existing rows first and document the choice.
  *Commit:* `fix(catalog): compute average stars in SQL and validate star range`

---

## Phase 2 — Security

- [ ] **2.1 Comment author from the authenticated user (S).**
  *Why:* `author` comes from the request body, so any writer can post as anyone.
  *Do:* take the author from the `Authentication` in `CommentController` (pass it into `CreateCommentCommand`) and stop accepting `author` in `CommentRequestDto` (or ignore it with a documented deprecation). Update the tests.
  *Commit:* `fix(catalog): derive comment author from the authenticated principal`
- [ ] **2.2 Token introspection hardening (M).** *(If you plan 7.1 soon, do 7.1 first.)*
  *Why:* every opaque-token request calls Google/GitHub/Facebook synchronously with no cache, and any unrecognized token falls through to Facebook.
  *Do:* in `config/`, add a caching decorator for `OpaqueTokenIntrospector` (Caffeine, key = SHA-256 of the token, TTL = min(token expiry, 60 s)); reject tokens that match no known prefix with `BadOpaqueTokenException` **before** any outbound call; make sure outbound HTTP calls have connect/read timeouts. Do not log tokens. Tests: cache hit avoids delegate call; unknown prefix makes no outbound call.
  *Commit:* `feat(catalog): cache token introspection and fail fast on unknown tokens`
- [ ] **2.3 Trusted `userId` in logs (S).**
  *Why:* `MdcRequestFilter` copies the client-supplied `X-User-Id` header into the MDC.
  *Do:* set `userId` from the `SecurityContext` after authentication (e.g. in a `HandlerInterceptor`), not from a header. Update `MdcRequestFilterTest`.
  *Commit:* `fix(catalog): take log userId from the security context`

---

## Phase 3 — Data and query performance

- [ ] **3.1 Missing FK indexes (S).** New migration: indexes on `products(category_id)` and `comments(product_id)`; drop the redundant `category_index`/`product_index` if 1.2 hasn't already. Verify with `EXPLAIN` in a `CatalogPersistenceIT` or a note in the PR. *Commit:* `perf(catalog): index foreign keys`
- [ ] **3.2 `updated_at` on bulk updates (S).** `@Modifying` JPQL in `ProductRepository` (`updateById`, `changeCategory`, `moveAllProductsToCategory`) bypasses `@UpdateTimestamp`. Add `p.updatedAt = CURRENT_TIMESTAMP` to each query. Test that `updated_at` changes. *Commit:* `fix(catalog): maintain updated_at on bulk updates`
- [ ] **3.3 N+1 and native queries (M).**
  *Do:* convert the native `CommentRepository` queries to derived or JPQL queries (the `@SQLRestriction` already filters soft-deleted rows; native queries with `Pageable` also risk a missing count query). Add `@EntityGraph(attributePaths = "product")` where `convertComment` touches the product. Stop embedding the full product list in category responses (return a count, or a paginated sub-resource). Whitelist sortable properties in `PageableSupport` (unknown property → 400). Add a Hibernate-statistics test asserting a bounded statement count for list endpoints.
  *Commit:* `perf(catalog): remove N+1 queries and whitelist sort properties`

---

## Phase 4 — Events and consistency

- [ ] **4.1 Idempotency done properly (M).**
  *Why:* keys are global, a failed request permanently burns its key, and the duplicate response carries no original result.
  *Do:* scope the key by principal + method + path; store a state (`IN_PROGRESS` / `COMPLETED` + status/body); release the key when the request fails with 5xx; replay the stored response for a completed duplicate; return 409 only for in-progress duplicates. Decide and document the Redis-outage policy (fail-open or fail-closed) in an ADR.
  *Commit:* `feat(catalog): scoped, replayable idempotency keys`
- [ ] **4.2 Read-your-writes (M).** Implement the ADR-005 recommendation: `@CachePut` on create/update so the next read comes from Redis instead of a possibly lagging replica; consider reading the confirmation inside the write transaction. Add an integration test. Update ADR-005 status. *Commit:* `fix(catalog): write-through cache to close read-after-write gap`
- [ ] **4.3 Cache failures must not fail requests (S).** Add a `CachingConfigurer`/`CacheErrorHandler` that logs and bypasses Redis errors on get/put/evict. Test with Redis stopped. *Commit:* `fix(catalog): treat cache errors as misses`
- [ ] **4.4 Transactional outbox (L, plan mode).**
  *Why:* events are sent after commit, fire-and-forget; a failed send loses the event.
  *Do, as separate commits:* (a) migration + `OutboxEvent` entity/repository (id, aggregate id, topic, key, payload JSON, headers, `created_at`, `sent_at`); (b) write the outbox row in the **same transaction** as the product change and remove `publishAfterCommit`; (c) a scheduled relay that claims unsent rows with `SELECT ... FOR UPDATE SKIP LOCKED`, sends to Kafka, marks `sent_at`, with retry/metrics; (d) integration tests: Kafka down → rows remain → recovered → delivered once per attempt (at-least-once; consumers dedupe on `eventId`). Add an ADR.
  *Commit:* `feat(catalog): transactional outbox for product events`
- [ ] **4.5 Richer, complete events (M).** *(Depends on 4.4.)* Add `ProductCategoryChanged` (covers moves and category delete), include `price`, `inStock`, `categoryName`, `version` and a `schemaVersion` field in payloads, and create the topics explicitly (`NewTopic`). Update `ProductEvent*` tests. *Commit:* `feat(catalog): publish complete, versioned product events`

---

## Phase 5 — API design

- [ ] **5.1 Validation in one place + sane limits (M).** Add `@NotBlank` to `ProductRequestDto.name/description` (null currently slips past `@Size`), `@Digits` and `@PositiveOrZero` on price, and relax the 3–20 name / 10–50 description limits (e.g. name 3–200, description 10–2000) with a migration widening `products.name`. Keep entity and DTO rules identical. Update `ProductValidationTest`/`CommandValidationTest`. *Commit:* `feat(catalog): consistent validation and realistic limits`
- [ ] **5.2 Separate create and update DTOs (S).** `PUT` currently accepts `name` and ignores it. Introduce `UpdateProductRequestDto` without `name`; keep OpenAPI docs accurate. Decide whether PUT returns 204 or the updated body. *Commit:* `refactor(catalog): dedicated update request DTO`
- [ ] **5.3 Comment API cleanup (M).** Return the comment `id`, point the `Location` header at it, drop the nested product from `CommentResponseDto` (use `productId`/`productName`), and add `DELETE /comments/{id}` for the author or an admin. Note breaking changes in the PR. *Commit:* `feat(catalog): comment ids, location header and delete`
- [ ] **5.4 Product search and filtering (M).** Optional query params on `GET /products`: `q` (name contains), `minPrice`, `maxPrice`, `inStock`, `categoryName`; implement with JPA `Specification`; sort whitelist from 3.3. Tests per filter plus combinations. *Commit:* `feat(catalog): product search and filtering`

---

## Phase 6 — Quality, observability, docs

- [ ] **6.1 Quieter logging (S).** Remove `MeasureAspect` (Micrometer already records `http.server.requests`) or drop it to DEBUG; move `show-sql` and Hibernate `SQL: DEBUG` / `type.descriptor: TRACE` out of `application-dev.yml` into an opt-in `debug` profile. *Commit:* `chore(catalog): reduce default logging noise`
- [ ] **6.2 Dead-code cleanup (S).** Remove unused `MapStructMapper` methods (DTO→entity reverse mappings), `VirtualThreads` if unused after 1.5, unused DTOs, and rename `CommentServerImplTest` → `CommentServiceImplTest`. Build and run all tests. *Commit:* `chore(catalog): remove dead code`
- [ ] **6.3 Coverage gate and CI (S).** JaCoCo currently only runs `prepare-agent`; add the `report` and `check` goals (start at the current coverage rounded down, e.g. 80% lines). Check the root `Jenkinsfile` runs `verify` for catalog. *Commit:* `build(catalog): enforce coverage threshold`
- [ ] **6.4 Docs match the code (M).** Rewrite `catalog/README.md` from the real controllers and OpenAPI output (paths are `/api/v1/catalog/...`, stock is a boolean, no Elasticsearch/MySQL/SKU). Fix ADR-005 drift (`@CacheEvict(key = "#id")`, nonexistent `ReadReplicaHealthIndicator`, `@SQLRestriction` not `@Where`). Add ADRs for outbox, idempotency and token caching. *Commit:* `docs(catalog): align README and ADRs with the code`
- [ ] **6.5 Health indicators (S).** Implement the replica health indicator the ADR mentions (reuse the cached probe), add readiness/liveness groups. *Commit:* `feat(catalog): replica health indicator and probe groups`

---

## Phase 7 — Separate the service from the monorepo

Move this phase up if extraction is your immediate goal; 7.1 should then come right after Phase 1.

- [ ] **7.1 Remove the `client` module dependency (M, plan mode).** `ResourceServerConfig` imports `CustomOpaqueTokenIntrospector` from `client`. Recommended: stop validating social opaque tokens in catalog and accept only JWTs issued by the auth service (token exchange happens at auth/gateway). If you must keep opaque tokens, copy the minimal introspector + principals + role→scope mapping into a catalog-local `security` package with timeouts. Then delete the `client` dependency from `catalog/pom.xml`. *Done when:* `./mvnw -pl catalog test` passes without building `client`. *Commit:* `refactor(catalog): drop dependency on shared client module`
- [ ] **7.2 Standalone POM (S).** Make `catalog/pom.xml` independent: parent `spring-boot-starter-parent` 4.1.0, explicit `java.version` 25, import the Spring Cloud BOM (2025.1.1), and keep each version property in one place (springdoc and otel-logback are currently duplicated). Add `.mvn/` and `mvnw` inside `catalog/`. Keep the module listed in the root aggregator if you still want monorepo builds. *Commit:* `build(catalog): standalone pom and wrapper`
- [ ] **7.3 Self-contained Dockerfile (S).** Build context = `catalog/`; copy `pom.xml`, `.mvn`, `mvnw`, then `src`; cache dependencies in a separate layer; remove `-pl catalog -am`. Verify `docker build catalog/` from the catalog folder. *Commit:* `build(catalog): self-contained Docker build`
- [ ] **7.4 Externalize configuration (M).** Replace hardcoded `localhost` values with env-backed properties (`${AUTH_ISSUER_URI:http://localhost:8082}`, Eureka, OTLP, Kafka, Redis, DB URLs). Make Vault and Eureka switchable (`SPRING_CLOUD_VAULT_ENABLED`, `EUREKA_CLIENT_ENABLED`). Use a catalog-owned Vault path instead of `auth-service`. Add `application-prod.yml` (no devtools/docker-compose, no root-token auth, Swagger off). *Commit:* `feat(catalog): environment-driven configuration and prod profile`
- [ ] **7.5 Run it alone (M).** Add a full standalone compose (catalog + postgres writer/reader + redis + kafka + a mock OIDC server such as `mock-oauth2-server` or Keycloak) so a new developer can run the service without the other modules. Document in README. *Commit:* `feat(catalog): standalone docker-compose with mock OIDC`
- [ ] **7.6 Per-module CI (S).** Make the `Jenkinsfile` (or a new pipeline in `catalog/`) build and test only when `catalog/**` changes. *Commit:* `ci(catalog): module-scoped pipeline`

---

## Phase 8 — Optional / nice to have

- [ ] ETag + `If-Match` on `PUT` to expose optimistic locking to clients.
- [ ] Spring Data auditing with `createdBy`/`updatedBy` from the principal.
- [ ] Scheduled purge job for soft-deleted rows older than N days.
- [ ] ArchUnit tests enforcing layering (controller → service → repository).
- [ ] Rate limiting (Resilience4j `RateLimiter`) on batch-get and search.
- [ ] Denormalized product rating (`avgStars`, `ratingCount`) maintained on comment events.
- [ ] Kafka dead-letter handling and a schema registry (Avro/JSON Schema) for events.
- [ ] Cursor (keyset) pagination for large lists.
- [ ] Consumer contract tests (Pact / Spring Cloud Contract) for the REST API and event schemas.
- [ ] Mutation testing with PIT on `service/` and `idempotency/`.
- [ ] Testcontainers reuse and a single shared container base to speed up the suite.

---

### Suggested sprint grouping

| Sprint | Tasks |
|---|---|
| 1 — Stop the bleeding | 0.1, 0.2, 1.1–1.6 |
| 2 — Trust | 2.1–2.3, 3.1–3.3 |
| 3 — Reliability | 4.1–4.5 |
| 4 — Product polish | 5.1–5.4, 6.1–6.5 |
| 5 — Extraction | 7.1–7.6, then Phase 8 as time allows |
