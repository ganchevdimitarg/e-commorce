# Catalog Module — Grade-A Hardening Design

**Date:** 2026-06-24
**Status:** Approved (pending written-spec review)
**Goal:** Raise the catalog module's audit score from **76/100 (B)** to **≥95/100 (A)**.

---

## 1. Context

A `/review` audit scored the `catalog` module 76/100. No Critical findings, but six
Warnings and several Suggestions across design, observability, and performance. The
weighted scorecard leaves no slack: reaching ≥95 requires landing essentially every
finding. This design covers all of them, sequenced into four independently-buildable
phases under one spec.

### Target scorecard

| Dimension (weight) | Now | Target | Levers |
|---|---|---|---|
| Correctness & Safety (25) | 8 | 10 | bulk-move fix, MDC trace correctness, auth double-decode fix |
| Convention compliance (20) | 8 | 10 | command DTOs, RESTful 201/Location, Kafka headers |
| Design & SOLID (20) | 6 | 9 | request/command/response split, encapsulation, Demeter |
| Design patterns (10) | 8 | 9 | command pattern, bulk update, trace via `Tracer` |
| Readability (15) | 8 | 9 | resource-oriented paths, dedup category lookups |
| Testability & tests (10) | 8 | 9 | stronger assertions, IT for endpoints + event headers |

Weighted projection ≈ **94.5–95**.

### Locked decisions

- **Contracts:** free to break **both** REST API and Kafka event schema.
- **API:** full RESTful redesign (resource paths, 201 + `Location`, 204 on delete).
- **Events:** ID-reference payload + trace/correlation headers.
- **Idempotency:** `Idempotency-Key` **required** on writes (missing → 400).
- **`correlationId`:** equals the event's `eventId` (one event = one correlation id).
- **Encapsulation:** field-level setters + defensive-copy collection getters.
- **Packaging:** phased implementation plan, single design doc.
- **Coverage gate:** **85% line**, 100% on domain logic.

---

## 2. Phase 1 — Domain commands + RESTful API

### Three DTO roles (separation of concerns)

| Role | Type(s) | Purpose |
|---|---|---|
| Request | `ProductRequestDto`, `CategoryRequestDto`, `CommentRequestDto` | inbound wire JSON, bean-validated |
| Command | `CreateProductCommand`, `UpdateProductCommand`, `MoveProductCommand` (new records) | service input; cross-field rules in compact constructors |
| Response | `ProductResponseDto`, `CategoryResponseDto`, `CommentResponseDto` | outbound only (reads/returns) |

Services **stop accepting `ProductResponseDto` as a write input**. Controllers map
`RequestDto → Command` via MapStruct, call the service, map result → `ResponseDto`.

### Resource-oriented paths

```
POST   /api/v1/catalog/products                      201 + Location   create
GET    /api/v1/catalog/products                      200 PageResponse  list
GET    /api/v1/catalog/products/{id}                 200               by id (cacheable)
GET    /api/v1/catalog/products/by-name/{name}       200               by name
PUT    /api/v1/catalog/products/{id}                 200               update
DELETE /api/v1/catalog/products/{id}                 204               delete
POST   /api/v1/catalog/products:batch-get            200               id-list lookup
POST   /api/v1/catalog/categories                    201 + Location    create category
DELETE /api/v1/catalog/categories/{name}             204               delete category
GET    /api/v1/catalog/categories                    200 PageResponse  list
POST   /api/v1/catalog/categories/{from}/products/{name}:move   200    move one
POST   /api/v1/catalog/categories/{from}/products:move-all      200    move all
POST   /api/v1/catalog/products/{name}/comments      201 + Location    create comment
GET    /api/v1/catalog/products/{name}/comments      200 PageResponse  list by product
GET    /api/v1/catalog/comments/by-author/{author}   200 PageResponse  list by author
GET    /api/v1/catalog/products/{name}/comments/avg-stars  200         avg stars
```

### Knock-on correctness improvement

Writes become **id-keyed** (`PUT/DELETE /products/{id}`), so the read cache evicts by
`#id` precisely via `@CacheEvict(key = "#id")` instead of `allEntries = true`. Update/delete
service methods take the id.

### Validation cleanup

`@NotBlank` / `@Size` on `@PathVariable` / `@RequestParam` replace the manual
`ValidationException` blank-checks inside the service methods. `@Validated` on controllers
(already present) triggers `ConstraintViolationException` → problem+json (handler exists).

---

## 3. Phase 2 — Events + observability

### Event schema (ID-reference + headers)

```java
sealed interface ProductEvent permits ProductCreated, ProductUpdated, ProductDeleted {
    String  eventId();      // UUID — consumer-side idempotency + correlationId
    String  productId();
    String  productName();
    Instant occurredAt();
}
```

`ProductEventPublisher.send` switches `KafkaTemplate.send(topic, key, value)` to a
`ProducerRecord` carrying headers:

| Header | Value |
|---|---|
| `traceId` | from MDC at send time |
| `correlationId` | `event.eventId()` |
| `eventType` | `created` / `updated` / `deleted` |

The service threads `product.getId()` into the publisher at every site (the entity is in
hand: created → saved entity; update/delete → entity from `findProductByName`). Publish
still occurs in `afterCommit`.

### Trace correctness (MDC)

Replace hand-rolled trace handling in `MdcRequestFilter`:

- Inject Micrometer `Tracer`; read `currentSpan().context()` to set MDC `traceId` **and**
  `spanId` with real ids — fixes the missing `spanId` (already referenced in
  `logback-spring.xml`) and the "raw `traceparent` header stored as `traceId`" bug.
- Keep `userId` (`X-User-Id`) and `serviceId`. Clear MDC in `finally`.

### Idempotency required on writes

`IdempotencyInterceptor`: for **state-changing** writes a **missing or blank**
`Idempotency-Key` now throws `ValidationException` (→ 400). Present key behaves as today
(Redis `SETNX`, 24h TTL, duplicate → 409).

The guard keys on mutation intent, not raw HTTP method: read-via-POST endpoints
(`/products:batch-get`) are **excluded**. The interceptor is registered against the
write paths explicitly (path-pattern include list) rather than blanket-matching all
POST/PUT/DELETE, so a lookup never requires a key.

### Auth hot-path (double-decode fix)

`ResourceServerConfig.isJwt` currently calls `jwtDecoder.decode(...)` purely to pick the
auth manager, then the provider decodes again. Replace with a cheap non-cryptographic
discriminator (JOSE structure: three base64url dot-segments with a decodable JSON header)
to route JWT vs opaque; the single authoritative decode stays in
`JwtAuthenticationProvider`. Any parse ambiguity falls back to the opaque manager.

---

## 4. Phase 3 — Performance + encapsulation

### Bulk move (correctness + Demeter + N+1)

- `moveOneProduct`: resolve `from`/`to` category ids once; look the product up via a
  repository finder (`findByNameAndCategoryId`) instead of the
  `getReferenceById(...).getProducts().stream()...` Demeter chain; single versioned
  `changeCategory`.
- `moveAllProducts`: **one** bulk `@Modifying` UPDATE —
  `UPDATE Product p SET p.category.id = :to, p.version = p.version + 1 WHERE p.category.id = :from` —
  returning the affected count for the `catalog.category.moved` metric. No per-row loop,
  no double category resolution. A category-wide reassignment is one logical operation, so
  the per-row optimistic-version dance is dropped for the bulk case.

### Encapsulation

On `Product` / `Category` / `Comment`:

- Replace class-level `@Setter` with **field-level `@Setter`** only on legitimately-mutable
  business fields (`name`, `description`, `price`, `inStock`, `characteristics`, `category`,
  comment fields). JPA-managed/derived fields are not openly settable.
  `[convention: narrowing setters for invariant safety]`
- Collection getters (`getProducts()`, `getComments()`) return `List.copyOf(...)`.
- Align `@Size`/column drift: set `Product.name` column `length` to match the
  `@Size(min=3, max=20)` business rule (and document the description bound).

### Misc convention cleanups

- Add `@Operation`/`@ApiResponses` to the batch-get endpoint (parity with siblings).
- Native `SELECT *` paginated query → JPQL where practical so `@SQLRestriction` applies
  automatically.

---

## 5. Phase 4 — Test hardening (gate: 85% line / 100% domain)

- **Unit:** migrate all service tests to command DTOs; strengthen weak assertions
  (`should_returnProductsPage_when_pageRequested` asserts mapped content). New cases for
  command compact-constructor validation.
- **Bulk move:** assert `moveAllProducts` issues a single bulk update (one repo call +
  affected-count metric).
- **Web slice (MockMvc):** new RESTful paths — 201 + `Location` on create, 204 on delete,
  400 when `Idempotency-Key` missing, 404 problem+json shape.
- **Events (IT, `RedisKafkaIntegrationBase`):** consume the record; assert payload
  (`eventId`, `productId`, `occurredAt`) **and** headers (`traceId`, `correlationId`,
  `eventType`).
- **Trace MDC:** `MdcRequestFilter` populates `traceId` + `spanId` from a stubbed `Tracer`
  and clears on exit.
- **Auth:** new `isJwt` discriminator routes JWT vs opaque without a full decode.
- `./mvnw clean verify -pl catalog -am` green at the end of **every** phase; bump the
  JaCoCo line gate to **85%**.

---

## 6. Out of scope

- MongoDB / Avro / schema registry (explicitly excluded by CLAUDE.md).
- Changes to other monorepo modules (gateway, profile, etc.).
- New business features beyond the existing catalogue surface.

---

## 7. Phase dependency order

P1 (commands + API) → P2 (events + observability) → P3 (perf + encapsulation) →
P4 (test hardening). Each phase leaves the build green and is independently committable.
Test updates for a phase land **with** that phase; Phase 4 is the final hardening/coverage
pass over the whole surface.
