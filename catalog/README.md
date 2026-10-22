# Catalog Service

Catalog owns the product catalogue of the e-commerce platform: products, the categories
that group them, and customer comments on products. Orders, payments, profiles and
notifications belong to sibling services.

- Service name: `catalog-service`
- Port: `8084`
- Base path: `/api/v1/catalog`
- Package: `com.ganchevdimitarg.catalog`

## Technology stack

| Concern | Choice |
|---|---|
| Language and runtime | Java 25 — virtual threads enabled |
| Framework | Spring Boot 4.1.0, Spring WebMVC |
| Persistence | PostgreSQL 16, Spring Data JPA / Hibernate, Flyway migrations |
| Cache | Redis (Lettuce, Jackson JSON serialisation) |
| Messaging | Kafka through a transactional outbox |
| Discovery and config | Eureka client, Spring Cloud Vault |
| Observability | Spring Boot Actuator, Micrometer → Prometheus, OpenTelemetry (OTLP) |
| API documentation | springdoc-openapi + Swagger UI (OAuth2 PKCE) |

Stock is the boolean `inStock` field on a product; there is no separate stock-keeping
model.

## Running locally

Run from the repository root:

```bash
./mvnw spring-boot:run -pl catalog
```

The default profile group maps to `dev` (`spring.profiles.group.default: dev`), which
serves the API on port **8084**. On `spring-boot:run`, the dev profile starts catalog's own
data plane from `catalog/compose.yaml`: PostgreSQL writer (`:5432`), PostgreSQL reader
(`:5433`), Redis (`:6379`) and Kafka (`:9092`). Vault (`:8200`) and the other shared
infrastructure come from the root `docker-compose.yml`. Eureka (`:8761`) and the auth
service (`:8082`) are not defined there: start them from their modules with
`./mvnw spring-boot:run -pl eureka-server` and
`./mvnw spring-boot:run -pl authentication`. An OTLP collector at `:4318` must be provided
separately for tracing.

Prerequisites: Docker, and `POSTGRES_USER`, `POSTGRES_PASSWORD` and
`VAULT_DEV_ROOT_TOKEN_ID` in the environment. `GATEWAY_CLIENT_SECRET` is optional (it has
an empty default) and is only needed for Swagger UI's OAuth2 PKCE login.

SQL diagnostics are opt-in; the default dev profile stays quiet:

```bash
SPRING_PROFILES_ACTIVE=dev,debug ./mvnw spring-boot:run -pl catalog
```

`application-debug.yml` enables `show-sql`, `format_sql`, Hibernate `SQL: DEBUG` and
`org.hibernate.type.descriptor: TRACE`.

### Standalone Compose stack

`compose-standalone.yaml`, run from `catalog/`, starts catalog with everything it needs —
PostgreSQL, Redis, Kafka and a mock OIDC issuer — without any sibling module:

```bash
cp .env.example .env
docker compose -f compose-standalone.yaml up -d --build
```

Prerequisites: Docker Desktop with Compose v2, and nothing else bound to host ports
**8084** (catalog), **8090** (mock issuer) or **29092** (Kafka's external listener).

Verify the stack:

```bash
# Aggregate health, including the database
curl -fsS http://localhost:8084/actuator/health

# A JWT from the mock issuer; copy access_token from the response
curl -s -X POST http://localhost:8090/default/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=dev&client_secret=dev'

# A secured read (SCOPE_catalog.read); expect 200
TOKEN="<paste access_token>"
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/api/v1/catalog/products?page=0&size=20"
```

Teardown, deleting the database volume:

```bash
docker compose -f compose-standalone.yaml down -v
```

Notes:

- One PostgreSQL serves both the writer and the reader pool; production uses a real read
  replica. This file declares the Compose project `catalog-standalone`, while `compose.yaml`
  keeps the directory-derived default `catalog`, so the two stacks do not clash and
  `down -v` here removes only this stack's containers and volumes. Postgres and Redis also
  publish no host ports, which keeps the host port map free.
- The mock issuer is `http://localhost:8090/default`; catalog validates that `iss` string
  but fetches signing keys over the Compose network from
  `http://mock-oauth2:8080/default/jwks`.
- Request tokens from `localhost`, not `127.0.0.1`: the `iss` claim embeds the host, and a
  `127.0.0.1` token fails issuer validation with 401.
- Swagger, Vault, Eureka and OTLP export are disabled in this stack. Logs go to the
  console: `docker compose -f compose-standalone.yaml logs catalog`.

## Configuration

| File | Purpose |
|---|---|
| `application.yml` | Profile-neutral base: application name, default `dev` profile group, JPA settings, Spring MVC path matching, virtual threads, actuator health probes/exposure and Prometheus, springdoc paths, the outbox cadence and rate-limit defaults. |
| `application-dev.yml` | Dev overlay: datasource credentials and writer/reader URLs, JWT issuer, Redis, Kafka, Eureka, Docker Compose Support (`compose.yaml`), devtools, tracing/OTLP, springdoc and the optional Vault import (`optional:vault://`, `http://localhost:8200`, token auth, backend `secret`, context `catalog/credentials`). |
| `application-prod.yml` | Production overlay, selected with `SPRING_PROFILES_ACTIVE=prod`: external endpoints are required from the environment with no localhost defaults, there is no Vault import, no Docker Compose support and no devtools, and Swagger is off. |
| `application-debug.yml` | Opt-in SQL diagnostics, activated with `SPRING_PROFILES_ACTIVE=dev,debug`. |

Catalog owns its Vault KV path `secret/catalog/credentials` (mount `secret`, context
`catalog/credentials`), and the import is optional in dev (`fail-fast: false`), so the
service starts without Vault. Secrets are always referenced as `${ENV_VAR}` — never
inlined.

Configuration keys that matter locally:

| Property | Value / purpose |
|---|---|
| `catalog.datasource.writer.url` | Primary datasource, `jdbc:postgresql://localhost:5432/catalog` |
| `catalog.datasource.reader.url` | Read-replica datasource, `jdbc:postgresql://localhost:5433/catalog` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8082` (auth service) |
| `catalog.outbox.relay-interval-ms` | Outbox relay cadence, default `1000` |
| `catalog.outbox.batch-size` | Rows claimed per relay run, default `20` |
| `catalog.outbox.send-timeout` | Broker acknowledgement timeout per row, default `5s` |
| `catalog.retention.purge-enabled` | Opt-in purge of expired soft-deleted rows, default `false` |
| `catalog.retention.retention` | Age after which a soft-deleted row is purged, default `90d` |
| `catalog.retention.cron` | Purge schedule, default `0 0 3 * * *` (nightly at 03:00) |
| `catalog.retention.batch-size` | Rows selected per purge batch, default `500` |
| `catalog.retention.max-batches-per-run` | Purge batches per run, default `100` |
| `catalog.rate-limit.enabled` | Per-principal rate limiting on product reads, default `true` |
| `catalog.rate-limit.search.permits` | Search requests per minute per principal, default `60` |
| `catalog.rate-limit.batch-get.permits` | Batch-get requests per minute per principal, default `10` |
| `spring.kafka.bootstrap-servers` | Spring Kafka bootstrap servers, `localhost:9092` |
| `kafka.bootstrapAddress` | Bootstrap address of the outbox producer, `localhost:9092` |

## Security

Catalog is a stateless OAuth2 **resource server** that accepts only JWTs issued by the auth
service.

- Issuer: `http://localhost:8082`; JWKS: `http://localhost:8082/oauth2/jwks`.
- Scopes come from the token's `scope` claim: `catalog.read` becomes
  `SCOPE_catalog.read`, `catalog.review` becomes `SCOPE_catalog.review` and
  `catalog.manage` becomes `SCOPE_catalog.manage`.
- Roles come from a dedicated `roles` claim, supplied as a collection or a space-delimited
  string; the `ROLE_` prefix is added when the issuer omits it (`ADMIN` → `ROLE_ADMIN`).
  `ROLE_*` values in the `scope` claim are still accepted as a deprecated compatibility path
  for tokens issued before the `roles` claim existed.
- `catalog.read` allows reads; `catalog.review` allows comment creation and deletion;
  `catalog.manage` allows product and category creation, updates, deletes and moves.
  `catalog.write` no longer exists and is not aliased: a comment moderator must not be able
  to reprice or delete products.
- Identity — comment authorship and comment-owner checks — comes from the JWT subject
  (`Authentication.getName()`). The gateway's `X-User-Id` / `X-User-Roles` headers are not
  trusted, because catalog is reachable directly on port 8084.
- Social opaque tokens are not accepted: they terminate at auth/gateway, and a social login
  must be exchanged for an auth-service JWT before catalog is called.
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health` and its group
  subpaths `/actuator/health/liveness` and `/actuator/health/readiness` (matcher
  `/actuator/health/**`), plus `/actuator/info` and `/actuator/prometheus`, are public;
  every other request needs a bearer token. Health details still require authorisation
  (`show-details: when-authorized`). Authentication and authorisation failures are rendered
  as `application/problem+json` (401 `UNAUTHENTICATED`, 403 `ACCESS_DENIED`).

## API

Every endpoint is under `/api/v1/catalog`. Reads require `SCOPE_catalog.read`, comment
creation and deletion require `SCOPE_catalog.review`, and product/category mutations require
`SCOPE_catalog.manage`, unless the authority column says otherwise.

| Method | Path | Authority | Request | Success |
|---|---|---|---|---|
| POST | `/api/v1/catalog/products?categoryName={name}` | `SCOPE_catalog.manage` | `ProductRequestDto`: `name`, `description`, `price`, `inStock`, `characteristics` | 201, `Location: /api/v1/catalog/products/{id}`, `ProductResponseDto` |
| GET | `/api/v1/catalog/products` | `SCOPE_catalog.read` | optional `q`, `minPrice`, `maxPrice`, `inStock`, `categoryName`; `page`, `size` ≤ 100, `sort` | 200, `PageResponse<ProductResponseDto>` |
| GET | `/api/v1/catalog/products/:cursor` | `SCOPE_catalog.read` | optional `q`, `minPrice`, `maxPrice`, `inStock`, `categoryName`; `cursor`, `limit` 1–100 (default 20), `sort` `createdAt,asc` or `createdAt,desc` (default `createdAt,desc`) | 200, `CursorPageResponse<ProductResponseDto>` |
| GET | `/api/v1/catalog/products/{id}` | `SCOPE_catalog.read` | UUID path | 200, `ProductResponseDto` (cached), `ETag` |
| GET | `/api/v1/catalog/products/by-name/{name}` | `SCOPE_catalog.read` | name path | 200, `ProductResponseDto`, `ETag` |
| POST | `/api/v1/catalog/products/:batch-get` | `SCOPE_catalog.read` | `ItemRequestDto`: 1–100 UUIDs | 200, `List<ProductResponseDto>`; 404 lists every missing id |
| PUT | `/api/v1/catalog/products/{id}` | `SCOPE_catalog.manage` | `UpdateProductRequestDto`: `description`, `price`, `inStock`, `characteristics`; optional `If-Match` header | 200, updated `ProductResponseDto`, `ETag` |
| DELETE | `/api/v1/catalog/products/{id}` | `SCOPE_catalog.manage` | — | 204 |
| POST | `/api/v1/catalog/categories` | `SCOPE_catalog.manage` | `CategoryRequestDto`: `name` | 201, `Location: /api/v1/catalog/categories/{name}`, `CategoryResponseDto` |
| GET | `/api/v1/catalog/categories` | `SCOPE_catalog.read` | pagination | 200, `PageResponse<CategoryResponseDto>` |
| DELETE | `/api/v1/catalog/categories/{name}` | `SCOPE_catalog.manage` | — | 204; 409 if products remain |
| POST | `/api/v1/catalog/categories/{from}/products/{productName}:move?to={to}` | `SCOPE_catalog.manage` | — | 200 |
| POST | `/api/v1/catalog/categories/{from}/products:move-all?to={to}` | `SCOPE_catalog.manage` | — | 200 |
| POST | `/api/v1/catalog/products/{productName}/comments` | `SCOPE_catalog.review` | `CommentRequestDto`: `title`, `text`, `star` 1–5; author from the JWT | 201, `Location: /api/v1/catalog/comments/{id}`, `CommentResponseDto` |
| GET | `/api/v1/catalog/products/{productName}/comments` | `SCOPE_catalog.read` | pagination | 200, `PageResponse<CommentResponseDto>` |
| GET | `/api/v1/catalog/comments/by-author/{author}` | `SCOPE_catalog.read` | pagination | 200, `PageResponse<CommentResponseDto>` |
| GET | `/api/v1/catalog/products/{productName}/comments/avg-stars` | `SCOPE_catalog.read` | — | 200, average `star` as a number |
| DELETE | `/api/v1/catalog/comments/{id}` | `SCOPE_catalog.review` or `ROLE_ADMIN` | — | 204; 403 if the caller is neither the author nor an admin |

`GET` by id and by name return a strong `ETag` derived from the product's optimistic-lock
`version`. `PUT` accepts an optional `If-Match`: absent, blank or `*` means no precondition,
a single strong tag (`"<version>"`) is enforced, and a weak, list-valued or non-numeric tag
is a 400 `VALIDATION_ERROR`. A stale tag is a 412 `PRECONDITION_FAILED`; a concurrent update
that loses the optimistic-lock race is a 409 `CONFLICT`.

### Response shapes

- `ProductResponseDto`: `id`, `name`, `description`, `price`, `inStock`, `characteristics`,
  `category` (`{id, name}`), `comments` (`CommentResponseDto[]`), `version` (optimistic-lock
  version, also the `ETag`), `avgStars` and `ratingCount`.
- `CategoryResponseDto`: `id`, `name`, `productCount`.
- `CommentResponseDto`: `id`, `title`, `text`, `star`, `author`, `productId`, `productName`.
- `PageResponse<T>`: `content`, `page`, `size`, `totalElements`, `totalPages`.
- `CursorPageResponse<T>`: `content`, `nextCursor` (opaque, `null` on the last page).

### Pagination and sorting

- Default page size is 20; the maximum is 100. A larger `size` returns 400
  `VALIDATION_ERROR` rather than being silently clamped.
- `sort` is whitelisted per resource. Products: `name`, `price`, `inStock`, `createdAt`,
  `updatedAt`. Categories: `name`, `createdAt`, `updatedAt`. Comments: `star`, `title`,
  `author`, `createdAt`, `updatedAt`. Any other sort property returns 400
  `VALIDATION_ERROR`.
- On `GET /api/v1/catalog/products`, `q` is a case-insensitive contains match on the product
  name; `categoryName` is resolved before the query, so an unknown category is a 404.

### Cursor pagination

`GET /api/v1/catalog/products/:cursor` is a keyset alternative for large or frequently
changing lists: it issues no count query, so deep pages cost the same as the first. The
offset endpoint `GET /api/v1/catalog/products` is unchanged and remains the default.

- Ordering is `(createdAt, id)` only. `sort` must be exactly `createdAt,asc` or
  `createdAt,desc` (the direction is case-insensitive); anything else — including
  `price,asc` — is a 400 `VALIDATION_ERROR`.
- `limit` is 1–100 (default 20). `nextCursor` is present only when another page exists;
  keep following it until it is `null`. A page shorter than `limit` is the last page.
- `cursor` is opaque. Do not build or edit it. It embeds a SHA-256 fingerprint of the
  filters and direction that minted it, so reusing a cursor with different `q`, price
  bounds, `inStock`, `categoryName` or `sort` returns 400 `VALIDATION_ERROR`, as does a
  malformed or truncated token.
- Filters are applied on every page; the token is a position, not a query.
- The keyset predicate and order can be served by the partial index
  `idx_products_created_at_id` on `products (created_at DESC, id DESC) WHERE deleted_at IS
  NULL` (`V17__index_product_cursor.sql`); an ascending page can use the same index with a
  backward scan. Soft-deleted products never enter it. Whether the planner chooses the index
  depends on the filters and table statistics.

Two-page flow:

```bash
# Page 1 — newest first, PC products named like "mouse"
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/api/v1/catalog/products/:cursor?q=mouse&categoryName=PC&limit=2"
# {
#   "content": [ { "id": "…", "name": "mouse", … }, { "id": "…", "name": "mouse pro", … } ],
#   "nextCursor": "eyJ2ZXJzaW9uIjoxLCJkaXJlY3Rpb24iOiJkZXNjIiwi…"
# }

# Page 2 — same filters, the token from page 1
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8084/api/v1/catalog/products/:cursor?q=mouse&categoryName=PC&limit=2&cursor=eyJ2ZXJzaW9uIjoxLCJkaXJlY3Rpb24iOiJkZXNjIiwi…"
# { "content": [ … ], "nextCursor": null }   ← last page
```

### Idempotency

Every `POST`, `PUT` and `DELETE` under `/api/v1/catalog/**` — except
`POST /api/v1/catalog/products/:batch-get`, which is a read expressed as POST — requires an
`Idempotency-Key` header. A missing header is a 400 `VALIDATION_ERROR`. The stored key is
scoped by JWT subject, HTTP method, request path and the client key (SHA-256, prefix
`catalog:idempotency:`, 24-hour TTL):

- a completed duplicate replays the stored status, `Content-Type`, `Location` and body;
- an in-progress duplicate returns 409 — the first request is still running;
- a Redis outage fails open: the request proceeds without protection (ADR-006).

### Rate limiting

The read-heavy product endpoints are throttled per principal with Resilience4j, in two
independent buckets, as defence in depth behind the gateway's edge limit:

| Bucket | Endpoints | Default |
|---|---|---|
| search | `GET /api/v1/catalog/products`, `GET /api/v1/catalog/products/:cursor` | 60 requests/minute |
| batch-get | `POST /api/v1/catalog/products/:batch-get` | 10 requests/minute |

`catalog.rate-limit.enabled` (default `true`) toggles enforcement. A throttled request
returns 429 `RATE_LIMIT_EXCEEDED` with a `Retry-After` header.

### Comments

- Creation takes the author from the JWT subject and returns
  `Location: /api/v1/catalog/comments/{id}`. Creation and deletion require
  `catalog.review`.
- Deletion is allowed for the comment's author or a caller holding `ROLE_ADMIN`; everyone
  else receives 403.

## Error model

Errors raised by catalog — domain failures and the request guards — use
`application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail`, `code`
and `timestamp`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Product not found: 3f1c2b34-5d6e-4f70-8a91-b2c3d4e5f601",
  "code": "NOT_FOUND",
  "timestamp": "2026-10-21T10:15:30.123456Z"
}
```

| Status | `code` | Typical cause |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request-parameter validation (`ConstraintViolationException`), page size above 100, sort property outside the whitelist, missing `Idempotency-Key`, malformed `If-Match` |
| 401 | `UNAUTHENTICATED` | Missing, expired or invalid JWT |
| 403 | `ACCESS_DENIED` | Missing scope, deleting someone else's comment without `ROLE_ADMIN`, or listing another user's comments without `ROLE_ADMIN` |
| 404 | `NOT_FOUND` | Unknown product, category, comment or category name in a product search |
| 409 | `CONFLICT` | Duplicate product/category name, category still holding products, in-progress idempotent request, optimistic-lock clash |
| 412 | `PRECONDITION_FAILED` | `If-Match` does not match the product's current `version` |
| 429 | `RATE_LIMIT_EXCEEDED` | Search or batch-get rate limit exhausted for the principal |
| 500 | `INTERNAL_ERROR` | Unexpected failure; the detail is generic and the cause is logged |

Framework-handled 400s — `@RequestBody @Valid` failures and unreadable or malformed bodies —
also return `application/problem+json`, but in the standard framework shape: `type`,
`title`, `status` and `detail` only. They carry no catalog `code`, `timestamp` or field-level
`errors` list.

## Events

Product changes are published through a **transactional outbox**: `OutboxEventWriter`
validates the payload against the topic's versioned JSON Schema
(`classpath:event-schemas/<topic>.v<schemaVersion>.json`; ADR-010) and inserts an
`outbox_events` row in the same transaction as the product change, so an event exists if
and only if the change committed. `OutboxRelay` then:

- runs every 1000 ms (`catalog.outbox.relay-interval-ms`) from `OutboxScheduler`;
- claims a batch (default 20, oldest first) where `sent_at IS NULL`,
  `dead_lettered_at IS NULL` and `next_attempt_at <= now()`, using
  `SELECT … FOR UPDATE SKIP LOCKED`;
- sends the stored JSON bytes through the outbox `KafkaTemplate` (`acks=all`, idempotent
  producer) and marks the row sent only after the broker acknowledges.

Delivery is **at-least-once**; consumers must deduplicate on the payload's `eventId`.

| Topic | Event |
|---|---|
| `catalog.product.created` | `ProductCreated` |
| `catalog.product.updated` | `ProductUpdated` |
| `catalog.product.deleted` | `ProductDeleted` |
| `catalog.product.category-changed` | `ProductCategoryChanged` |

Payloads carry `eventId`, `productId`, `productName`, `price`, `inStock`, `categoryName`,
`version`, `schemaVersion` and `occurredAt`; `ProductCategoryChanged` also carries
`previousCategoryName`. Record headers carry `eventType`, `correlationId` (the event's
`eventId`) and `traceId`.

Tuning and failure behaviour:

- `catalog.outbox.enabled` (default `true`) gates scheduled runs only — `relayOnce()` always
  delivers, so tests and operators can drive a run directly;
- `catalog.outbox.batch-size` (default `20`) and `catalog.outbox.send-timeout` (default
  `5s`) bound each run;
- a retryable failed send (broker, network or timeout) increments `attempts`, stores
  `last_error`, schedules the next attempt with exponential backoff (1, 2, 4, 8, 16, 32,
  then 60 seconds) and stops the batch;
- a non-retryable ("poison") failure — a serialisation, oversized-record or invalid-record
  error — parks the row instead: it sets `dead_lettered_at` and `dead_letter_reason`,
  increments `catalog.outbox.dead_lettered` and excludes the row from future claims. There
  is no `.DLT` topic because a payload that cannot be serialised cannot be published. See
  ADR-009.

## Caching and read replica

**Cache.** There is one Redis cache, `product`, with a 10-minute TTL, the `catalog:v3:` key
prefix (`catalog:v3:product::<uuid>`) and Jackson JSON values.

- `getProductById` is `@Cacheable(cacheNames = "product", key = "#id")`.
- `createProduct` and `updateProduct` build the response DTO inside the write transaction
  and call `ProductCache.put(...)`, which registers an `afterCommit` synchronisation — a
  rolled-back write never populates the cache.
- `deleteProduct`, comment creation/deletion and a single-product category move call
  `ProductCache.evict(...)`; category deletion and move-all call `ProductCache.evictAll()`.
- Catalog has no `@CacheEvict` annotation. Cache-provider failures are logged and treated as
  cache misses, so a Redis outage degrades latency but not request success.

**Read replica.** `DataSourceRouter` sends `@Transactional(readOnly = true)` work to the
PostgreSQL replica (`:5433`); writes and Flyway migrations always use the primary (`:5432`).
A shared `ReplicaHealthProbe` caches an `isValid(1)` check for 2 seconds; while the replica
is unreachable, the router falls back to the writer. Browse reads (`searchProducts`,
`getProductByName`, batch-get) are eventually consistent; `getProductById` after a
create/update is read-your-writes because the cache is populated after commit. See ADR-005.

## Retention

Catalog soft-deletes products, categories and comments. An optional scheduled job
hard-deletes rows that were soft-deleted longer ago than the retention window — the one
deliberate exception to the soft-delete rule, where the window is the audit window
(`docs/decisions.md`).

The job is **off by default**. Opting in enables a bean that runs on
`catalog.retention.cron` and, each run, deletes in foreign-key order until a batch removes
nothing or `catalog.retention.max-batches-per-run` is reached:

1. comments soft-deleted before the cutoff;
2. products soft-deleted before the cutoff, together with **all** their comments (live or
   soft-deleted);
3. categories soft-deleted before the cutoff that no longer hold any product row.

The delete statements use native SQL (JPA's `@SQLRestriction` hides soft-deleted rows).
Each selection — expired comments, expired products, empty expired categories — is limited
to `catalog.retention.batch-size`, default `500`, so a large backlog is purged over several
batches and committed batch by batch. A selected product's comments are all deleted with
it, so the total number of deleted rows is not itself capped by `batch-size`. Each batch
that deletes something adds its counts to the `catalog.retention.purged` counter (tagged
`entity=comments|products|categories`) and emits one structured INFO line; a batch that
deletes nothing logs at DEBUG, so a multi-batch run logs one line per batch. See the
retention rows in the configuration table above.

## Observability

| Endpoint | Contents |
|---|---|
| `/actuator/health` | Aggregate health, including the `replica` component |
| `/actuator/health/liveness` | `livenessState`, `ping` |
| `/actuator/health/readiness` | `readinessState`, `db` |
| `/actuator/prometheus` | Micrometer metrics in Prometheus format |
| `/actuator/info` | Info endpoint; empty unless info contributors are configured |

Health details are shown to authorised callers (`management.endpoint.health.show-details:
when-authorized`). The `replica` component is always `UP`; its `reader`
(`reachable`/`unreachable`) and `route` (`reader`/`writer`) details show whether reads are
served by the replica or have degraded to the writer. Redis and the replica are deliberately
not part of the readiness group: cache failures are misses and a replica outage degrades to
the primary, so neither should make a pod unready.

Tracing is exported over OTLP/HTTP to `http://localhost:4318/v1/traces` with a 0.1 sampling
probability. Business metrics follow `catalog.<entity>.<action>`:

- `catalog.product.created`, `catalog.product.updated`, `catalog.product.deleted`;
- `catalog.category.created`, `catalog.category.deleted`, `catalog.category.moved`;
- `catalog.comment.created`, `catalog.comment.deleted`;
- `catalog.outbox.published`, `catalog.outbox.failed` and
  `catalog.outbox.dead_lettered` counters, plus the `catalog.outbox.pending` gauge;
- `catalog.retention.purged`, tagged `entity` (`comments`, `products`, `categories`).

## Testing

Run from the repository root:

```bash
# Unit suite — tests tagged "unit"
./mvnw clean test -pl catalog -am -Dgroups=unit

# Unit + Testcontainers integration tests + JaCoCo gates
./mvnw clean verify -pl catalog -am
```

Testcontainers-backed integration tests extend `AbstractIntegrationTest` /
`RedisKafkaIntegrationBase`; the `@WebMvcTest` web-slice tests and
`RatingBackfillMigrationIT` are also tagged `integration` but start no containers. Docker
must be running for the full `verify`. JaCoCo enforces 90 % line coverage for the bundle
and 100 % for `com.ganchevdimitarg.catalog.service.*`.

### Mutation testing (opt-in)

PIT mutation-tests the `service.*` implementations and the `idempotency.*` classes using
unit tests only. The `pitest` profile is opt-in and is not bound to `verify`, so the normal
build never runs it. Run from `catalog/` — the profile lives in the catalog POM, so the
root-level `-pl catalog -am` form used above does not activate it:

```bash
./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage
```

Reports are written to `target/pit-reports/index.html` (HTML and XML, with no timestamped
directories). The mutation threshold is 85 %; the recorded baseline is 88 % (141/160
mutants killed). `*IT` classes are deliberately not selected, so PIT needs no Docker and
never starts Testcontainers.

### Contract tests (producer)

Catalog publishes producer-side Spring Cloud Contract stubs for its REST API. The contracts
live in `src/test/resources/contracts/catalog/`; the `spring-cloud-contract-maven-plugin`
generates JUnit 5 tests into `target/generated-test-sources/contracts` during
`generate-test-sources`, and they run with the rest of the suite in `./mvnw clean verify`.
The stubs jar is `target/catalog-0.0.1-SNAPSHOT-stubs.jar`.

The generated tests drive a standalone MockMvc wired with the real `ProductController` and
`ControllerExceptionHandler`; only `ProductService` is mocked (`ContractVerifierBase`), so
no Spring context, database or container is needed. Covered: get by id, list, create,
update (`If-Match`/`ETag`), batch-get and cursor pagination, including the `version`,
`avgStars` and `ratingCount` fields. No interceptors or method security are installed in
this harness, so idempotency enforcement, rate limiting and `@PreAuthorize` checks are
outside these tests; the mutation contracts still send the `Idempotency-Key` header the
real service requires, and enforcement is covered by the dedicated idempotency, rate-limit
and security tests.

Stubs are for future consumers. The `order` module is the only in-repo consumer today, but
the path it configures — `/catalog/product/get-products-id` — does not exist; the real
batch-get contract is `POST /api/v1/catalog/products/:batch-get`, and fixing `order` is a
separate cross-module change. No in-repo module consumes catalog events, so event
contracts stay with the JSON Schema files (ADR-010).

## Continuous integration

`catalog/Jenkinsfile` owns the catalog build. The Jenkins job must be a **Multibranch
Pipeline** (or otherwise provide changelog data for the `changeset` gate) configured with
**Script Path `catalog/Jenkinsfile`** — a plain Pipeline job without changelog data cannot
evaluate the gate.

- Every working stage runs only when a `catalog/**` file changed; a push that touches no
  catalog file skips the build entirely.
- Build and test runs `./mvnw -B clean verify` from `catalog/` — the module wrapper is the
  only Maven entry point, and no root-reactor `-pl catalog -am` build is involved.
- The image is built from the `catalog/` context and tagged `<registry>/catalog:<git-sha>`
  (for example `catalog:3c0ae97`); push and deploy additionally require the `main` branch.

**Root-pipeline cutover prerequisite.** The repository-root `Jenkinsfile` still runs
`docker build -f catalog/Dockerfile .` with the repository root as the context. Since the
Dockerfile now expects the `catalog/` context, that stage fails. Until the root Docker
stage is changed to `docker build -f catalog/Dockerfile catalog` — or the root job's
catalog stages are disabled in Jenkins — the catalog-owned changeset gate cannot fully
take effect, because the root job still builds catalog. The catalog-owned pipeline cannot
change a file outside `catalog/`.

## Project references

- Architecture decision records: `docs/adr/` — ADR-005 read-replica staleness, ADR-006
  idempotency, ADR-007 transactional outbox, ADR-008 JWT-only resource server.
- Decision log: `docs/decisions.md`.
- Schema: `src/main/resources/db/migration/` (the highest `V<n>` there is the latest).
  Flyway owns every schema change (`ddl-auto: validate`); committed migrations are never
  edited.
- Module guidance: `CLAUDE.md`.
