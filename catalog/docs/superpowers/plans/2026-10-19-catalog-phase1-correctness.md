# Catalog Hardening — Phase 1 Correctness Bugs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the six correctness bugs (1.1–1.6) listed under Phase 1 of `catalog/TODO.md`:
security exceptions returning 500, name uniqueness colliding with soft-deleted rows, silent
category→product cascade, stale product cache, unbounded and connection-hungry batch-get, and
in-memory star averaging with unvalidated ratings.

**Architecture:** Six independent fixes, each its own session and commit, always test-first
(failing test → fix → green). Two new Flyway migrations: `V9` (partial unique indexes) and
`V10` (star range check). No cross-module changes; no changes to other services.

**Tech Stack:** Java 25 · Spring Boot 4.1.0 · WebMVC · JPA/Hibernate · PostgreSQL 16 ·
Redis · Flyway · JUnit 5 + AssertJ + Mockito · Testcontainers · Maven wrapper.

---

## Context

`catalog` is the reference module for the monorepo (see `catalog/CLAUDE.md`), but six
correctness bugs remain before hardening can move on to security and performance work:

- Method security lives on the service layer (`@PreAuthorize`), yet the catch-all
  `@ExceptionHandler(Exception.class)` turns `AccessDeniedException` into a 500. A read-only
  caller gets 500 instead of 403, and DB constraint races (duplicate names) surface as 500
  instead of 409.
- Name uniqueness is enforced by full-table unique constraints, while lookups use
  `@SQLRestriction("deleted_at IS NULL")`. Soft-deleted names therefore collide on re-create.
- `Category.products` cascades `ALL`, so deleting a category silently soft-deletes its products
  with no events and no cache eviction.
- Only update/delete invalidate the product cache; comment creation and category moves leave
  `ProductResponseDto` (which embeds category and comments) stale for the 10-minute TTL.
- `POST :batch-get` accepts unbounded ids and opens one virtual thread + DB connection per id.
- `getAvgStars` loads every comment into memory, and `star` is an unvalidated `double`.

Intended outcome: denied access is 403, constraint conflicts are 409, soft-deleted names can be
reused, category deletion is explicit, product reads are never stale after comment/move
operations, batch-get is bounded to one query, and ratings are averaged in SQL and range-checked.

---

## Decisions taken

- **Star stays `double` (non-breaking)** — confirmed with the user on 2026-10-19. Add
  `@DecimalMin("1")` / `@DecimalMax("5")`; do not switch to `int` and do not alter the column type.
- **Explicit exception handlers, no rethrow** — the catch-all is the bug; rethrowing from an
  advice method is less predictable than a dedicated handler. Codes mirror the filter-chain
  handlers: `ACCESS_DENIED` (403) and `UNAUTHENTICATED` (401).
- **Cache eviction after commit** — evicting inside a `@Transactional` method can race a
  concurrent read that repopulates stale data before the transaction commits. Use a small
  `ProductCacheEvictor` that registers an `afterCommit` synchronisation.
- **Unknown product in `getAvgStars` keeps returning `0.0`** — preserves existing behaviour;
  do not turn it into a 404 in this phase.
- **Migrations never clamp silently** — `V10` raises and fails the migration if existing rows
  are outside 1–5; no data is rewritten without a human decision.
- **Task order:** 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6. Dependencies: 1.4 follows 1.3 (same
  service class); 1.2's HTTP-409 expectation depends on 1.1; 1.2 reserves `V9`, 1.6 reserves
  `V10`.

---

## Global constraints

- **Scope:** stay inside `catalog/`. Never edit another module.
- **Branch:** work on `catalog-hardening`. Git currently reports `main`; verify with
  `git branch --show-current` and create/switch the branch before the first commit. The
  `block-main-commit` hook forbids committing on `main`.
- **Wrapper only:** run Maven from the repo root in Git Bash (`./mvnw ...`). In PowerShell,
  quote dotted `-D` flags (e.g. `'-Dsurefire.failIfNoSpecifiedTests=false'`).
- **Tests first:** write the failing test, run it, then fix. Test names:
  `should_<expected>_when_<condition>`.
- **ITs:** extend `AbstractIntegrationTest` (Postgres) or `RedisKafkaIntegrationBase`
  (Redis/Kafka); never H2/EmbeddedMongo; never `Thread.sleep()`.
- **Flyway:** never edit `V1`–`V8`; add `V9`/`V10`; `IF [NOT] EXISTS` guards where possible;
  `ddl-auto=validate` stays.
- **One task = one commit.** Confirm each commit subject with the user before committing
  (global `CLAUDE.md` rule). Stage explicit paths — never `git add -A` (the repo already has
  unrelated untracked files).
- **Attribution:** end every commit body with
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- **British English** in prose, comments and log messages.
- **Docker Desktop** must be running — the full verify starts Testcontainers.

---

### Task 1: 1.1 — Return 403 for denied access and 409 for integrity violations

**Files:**
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/exception/ControllerExceptionHandler.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/exception/ControllerExceptionHandlerTest.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/product/ProductControllerMvcTest.java`
- Create: `catalog/src/test/java/com/ganchevdimitarg/catalog/security/MethodSecurityHttpIT.java`

**Interfaces:**
- Produces: an advice that maps `AccessDeniedException` → 403 `ACCESS_DENIED`,
  `AuthenticationException` → 401 `UNAUTHENTICATED`, `DataIntegrityViolationException` → 409
  `CONFLICT` as `application/problem+json`. Later tasks rely on the 409 mapping.
- Consumes: nothing.

**Steps:**

- [x] **Step 1: Write the failing handler tests.** In `ControllerExceptionHandlerTest` add:
  - `should_return403ProblemDetail_when_accessDenied` — call the new handler with
    `new AccessDeniedException("denied")`, assert status 403, content type
    `application/problem+json`, `code = ACCESS_DENIED`, detail
    `You do not have permission to access this resource` (same wording as
    `ProblemAccessDeniedHandler`).
  - `should_return401ProblemDetail_when_authenticationException` — status 401,
    `code = UNAUTHENTICATED`.
  - `should_return409ProblemDetail_when_dataIntegrityViolation` — construct
    `new DataIntegrityViolationException("duplicate key")`, assert 409 and `code = CONFLICT`,
    and assert the detail does not contain the raw cause message.
  Run the unit tests and confirm they fail to compile (handlers absent) — then implement.
- [x] **Step 2: Implement the handlers** in `ControllerExceptionHandler`:

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return problemResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "You do not have permission to access this resource");
}

@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
    log.warn("Authentication failed: {}", ex.getMessage());
    return problemResponse(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "Authentication is required to access this resource");
}

@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ProblemDetail> handleIntegrityViolation(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation", ex);
    return problemResponse(HttpStatus.CONFLICT, "CONFLICT",
            "The request conflicts with existing data");
}
```

Imports: `org.springframework.security.access.AccessDeniedException`,
`org.springframework.security.core.AuthenticationException`,
`org.springframework.dao.DataIntegrityViolationException`. Keep the catch-all.
- [x] **Step 3: Prove the real method-security path.** Create
  `security/MethodSecurityHttpIT extends AbstractIntegrationTest` with
  `@AutoConfigureMockMvc` and `@MockitoBean StringRedisTemplate` (stub
  `opsForValue().setIfAbsent(...)` to `true`, as the MVC slice tests do — this avoids a Redis
  container; the deny path never touches the cache). Test:

```java
@Test
void should_return403ProblemJson_when_writeEndpointCalledWithReadScope() throws Exception {
    mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                    .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "phase1-403")
                    .content("""
                            {"name":"mouse","description":"WiFi mouse USB","price":1,
                             "inStock":true,"characteristics":""}
                            """))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}
```

  Run it against the current code first: it must fail with **500** (this is the regression).
  The real `@PreAuthorize` throws before the service body runs, so no DB fixture is needed.
- [x] **Step 4: Add MVC slice coverage for the mapping.** In `ProductControllerMvcTest`, stub
  `productService.updateProduct(...)` to throw `AccessDeniedException` and assert 403
  `ACCESS_DENIED`; stub `productService.createProduct(...)` to throw
  `DataIntegrityViolationException` and assert 409 `CONFLICT`. (The mocked service is not
  proxied by method security, so the slice test proves advice mapping, not authorisation.)
- [x] **Step 5: Verify.** Unit loop, then full verify (see Verification). The new IT and all
  existing security tests must be green.
- [x] **Step 6: Commit** — subject `fix(catalog): return 403 for denied access and 409 for integrity violations`.

**Done when:** the full-context IT gets 403 problem+json with code `ACCESS_DENIED`, the
integrity-violation test gets 409 `CONFLICT`, and no existing test regresses.

**Gotchas:** Spring Security 6/7 throws `AuthorizationDeniedException`, a subclass of
`AccessDeniedException` — one handler covers it. `AuthenticationException` cannot normally reach
`@ControllerAdvice` (the filter chain handles it); the handler is defensive and needs a unit test
for coverage. `DataIntegrityViolationException` also covers NOT NULL/FK violations, so keep the
client detail generic and log the cause server-side.

---

### Task 2: 1.2 — Name uniqueness applies only to live rows

**Files:**
- Create: `catalog/src/main/resources/db/migration/V9__partial_unique_live_names.sql`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/domain/Product.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/domain/Category.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/persistence/CatalogPersistenceIT.java`

**Interfaces:**
- Produces: partial unique indexes `ux_products_name_active` and `ux_categories_name_active`
  (`(name) WHERE deleted_at IS NULL`), replacing full-table unique constraints.
- Consumes: 1.1's 409 handler for the race path (service pre-check handles the common path).

**Steps:**

- [x] **Step 1: Inspect the real constraint names** (do not hardcode). Against a running dev
  DB or the Testcontainers Postgres:

```sql
SELECT t.relname, c.conname, c.contype
FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
WHERE t.relname IN ('categories', 'products') AND c.contype = 'u';
```

  Expected: `category_name` (V3) **and** an auto-named `categories_name_key` (V1 column-level
  `unique`), plus `product_name` (V3). Confirm before writing the migration.
- [x] **Step 2: Write the failing persistence tests first** in `CatalogPersistenceIT`:
  - `should_recreateName_when_previousRowWasSoftDeleted` — save a category with a unique test
    name, `delete(...)`, `entityManager.flush()`, save a new category with the same name →
    succeeds. Repeat the same flow for a product (category + product fixtures).
  - `should_rejectDuplicateLiveName_when_bothRowsAreActive` — two `saveAndFlush` with the same
    live name → `DataIntegrityViolationException`.
  Run them; the first fails with `DataIntegrityViolationException` on the current schema.
- [x] **Step 3: Add `V9__partial_unique_live_names.sql`.** Discover and drop every unique
  constraint on the `name` columns, drop the redundant indexes, create partial unique indexes,
  and fail loudly if anything survived:

```sql
-- Drop every unique constraint on categories.name / products.name, whatever it is called.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT t.relname AS tbl, c.conname AS cons
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.contype = 'u'
          AND t.relname IN ('categories', 'products')
          AND c.conkey = ARRAY[
              (SELECT a.attnum FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attname = 'name')
          ]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.tbl, r.cons);
    END LOOP;
END $$;

DROP INDEX IF EXISTS category_index;
DROP INDEX IF EXISTS product_index;

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_name_active
    ON categories (name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_products_name_active
    ON products (name) WHERE deleted_at IS NULL;

-- Fail loudly if a full-table unique constraint survived.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.contype = 'u' AND t.relname IN ('categories', 'products')
          AND c.conkey = ARRAY[
              (SELECT a.attnum FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attname = 'name')
          ]::smallint[]
    ) THEN
        RAISE EXCEPTION 'A unique constraint on name still exists';
    END IF;
END $$;
```

  Postgres DDL is transactional, so a failed assertion rolls the whole migration back.
- [x] **Step 4: Update the entity mappings** to match (not required by `validate`, but keeps
  the mapping honest):
  - `Product`: remove `uniqueConstraints` and `indexes` from `@Table`.
  - `Category`: remove `uniqueConstraints` and `indexes` from `@Table`; remove
    `unique = true` from `@Column(name = "name", ...)`.
- [x] **Step 5: Verify** unit + full verify. Flyway applies `V9` on the Testcontainers
  database; `ddl-auto=validate` must still pass.
- [x] **Step 6: Commit** — subject `fix(catalog): make name uniqueness apply only to non-deleted rows`.

**Done when:** create → delete → recreate with the same name succeeds for products and
categories; two live duplicates still throw at the DB; the service pre-check still returns 409
for the normal path. The HTTP 409 for the DB race is covered by Task 1.

**Gotchas:** `categories.name` has **two** unique constraints on a fresh DB — the V1
column-level one and V3's named one. `findByName` already filters soft-deleted rows via
`@SQLRestriction`, so no service change is needed. Use unique names per test: the Postgres
container is a singleton shared across IT classes and several tests do not roll back.
`ON CONFLICT (name)` without the predicate would no longer work — nothing uses it today.

---

### Task 3: 1.3 — Refuse to delete categories that still contain products

**Files:**
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/domain/Category.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/repository/ProductRepository.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/service/category/CategoryServiceImpl.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/category/CategoryServiceImplTest.java`

**Interfaces:**
- Produces: `CategoryServiceImpl.deleteCategory` throws `ConflictException` (409) when live
  products exist; `Category.products` no longer cascades.
- Consumes: nothing.

**Steps:**

- [x] **Step 1: Write the failing test.** Add
  `should_throwConflict_when_categoryStillHasProducts` — stub
  `categoryRepository.findByName(name)` and `productRepository.existsByCategory_Id(id)` →
  `true`; assert `ConflictException` and `verify(categoryRepository, never()).delete(any())`.
  Run it; it fails because the current code deletes.
- [x] **Step 2: Add the repository query** (underscore forces property traversal to
  `category.id`; it honours `@SQLRestriction`, so only live products block deletion):

```java
boolean existsByCategory_Id(UUID categoryId);
```

- [x] **Step 3: Remove the cascade** in `Category`:

```java
@OneToMany(mappedBy = "category", targetEntity = Product.class)
@JsonIgnore
private List<Product> products;
```

- [x] **Step 4: Change `deleteCategory`**:

```java
Category category = requireCategory(categoryName);
if (productRepository.existsByCategory_Id(category.getId())) {
    throw new ConflictException(
            "Category " + categoryName + " still contains products; move them first");
}
categoryRepository.delete(category);
```

- [x] **Step 5: Update the existing delete test** to stub `existsByCategory_Id` → `false` and
  verify `delete(category)` (not `deleteByName`).
- [x] **Step 6: Verify** unit + full verify.
- [x] **Step 7: Commit** — subject `fix(catalog): refuse to delete categories that still contain products`.

**Done when:** deleting a category with live products is a 409 and leaves products untouched;
deleting an empty category still soft-deletes it.

**Gotchas:** soft-deleted products do not block deletion (correct — they are hidden). A
soft-deleted product may keep pointing at a soft-deleted category; the FK row still exists, so
there is no FK violation and no cascade. Do not delete products from the category.

---

### Task 4: 1.4 — Evict the product cache on comment and category changes

**Files:**
- Create: `catalog/src/main/java/com/ganchevdimitarg/catalog/cache/ProductCacheEvictor.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/service/comment/CommentServiceImpl.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/service/category/CategoryServiceImpl.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/cache/ProductCacheIT.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/comment/CommentServerImplTest.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/category/CategoryServiceImplTest.java`

**Interfaces:**
- Consumes: Task 3's `deleteCategory` (empty-only) and the existing `product` cache
  (`@Cacheable` on `getProductById`, key `#id`).
- Produces: `ProductCacheEvictor.evict(UUID)` / `evictAll()`, applied after commit.

**Steps:**

- [x] **Step 1: Write the failing ITs first** in `ProductCacheIT` (it already has a Redis
  container and ordered tests):
  - `should_evictProduct_when_commentCreated` — populate the cache via
    `productService.getProductById(PRODUCT_ID)`, call
    `commentService.createComment(new CreateCommentCommand("great", "thorough review text", 5.0, "joe", PRODUCT_NAME))`,
    then assert `cacheManager.getCache("product").get(PRODUCT_ID)` is `null`.
  - `should_evictProduct_when_productMoved` — populate the cache, call
    `categoryService.moveOneProduct(new MoveProductCommand("cache-test-cat", "cache-test-cat-2", PRODUCT_NAME))`,
    assert the key is gone.
  - `should_evictAllProducts_when_allProductsMoved` — populate the cache, call
    `categoryService.moveAllProducts(...)`, assert the cache has no entry for `PRODUCT_ID`.
  Add a second category in `@BeforeEach` (and reset the product's `category_id`/`version`)
  so the move tests are repeatable. Use `@WithMockUser` with read + write scopes.
  Run them; they fail because only update/delete evict today.
- [x] **Step 2: Add `ProductCacheEvictor`**:

```java
@Component
@RequiredArgsConstructor
public class ProductCacheEvictor {
    private static final String CACHE = "product";
    private final CacheManager cacheManager;

    public void evict(UUID productId) {
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) cache.evict(productId);
        });
    }

    public void evictAll() {
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) cache.clear();
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override public void afterCommit() { action.run(); }
                    });
        } else {
            action.run();
        }
    }
}
```

  (`org.springframework.cache.Cache`, `CacheManager`; `TransactionSynchronization(Manager)`.)
- [x] **Step 3: Wire it in:**
  - `CommentServiceImpl.createComment`: after `saveAndFlush`, call
    `productCacheEvictor.evict(product.getId())`.
  - `CategoryServiceImpl.moveOneProduct`: after the successful update, call
    `productCacheEvictor.evict(product.getId())`.
  - `CategoryServiceImpl.moveAllProducts`: call `productCacheEvictor.evictAll()` (cached
    `ProductResponseDto`s embed the category and its product list, so every entry is stale).
  - `CategoryServiceImpl.deleteCategory`: call `productCacheEvictor.evictAll()` defensively
    (after Task 3 this can only delete empty categories, so it is cheap insurance and matches
    TODO 1.4).
- [x] **Step 4: Update unit tests** — inject a mocked `ProductCacheEvictor` into the
  `CommentServiceImpl` / `CategoryServiceImpl` constructors and verify:
  `evict(productId)` on comment creation and single move; `evictAll()` on bulk move and delete.
  These assertions keep the new `service.*` lines covered for the 100 % JaCoCo package gate.
- [x] **Step 5: Verify** unit + full verify (Redis container required).
- [x] **Step 6: Commit** — subject `fix(catalog): evict product cache on comment and category changes`.

**Done when:** all three new ITs pass and the cached DTO is gone immediately after a comment or
category change (after commit), with existing update/delete eviction unchanged.

**Gotchas:** eviction must run **after commit**, otherwise a concurrent read can repopulate the
cache with pre-commit data. `Cache.evict(UUID)` uses the same key as `@Cacheable(key = "#id")`
(the Redis key serializer stringifies the UUID). The helper lives outside `service.*`, so the
100 % line gate does not apply to it — but the service call sites still need coverage.

---

### Task 5: 1.5 — Bound batch-get and load products in one query

**Files:**
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/dto/product/ItemRequestDto.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/repository/ProductRepository.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/service/product/ProductServiceImpl.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/product/ProductServiceImplTest.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/product/ProductControllerMvcTest.java`

**Interfaces:**
- Consumes: `ProductRepository.findAllById` (new `@EntityGraph` override).
- Produces: batch-get bounded to 100 ids, one `findAllById` query, input order preserved, and
  a `NotFoundException` listing **all** missing ids in request order.

**Steps:**

- [x] **Step 1: Write the failing service tests** in `ProductServiceImplTest`:
  - Rewrite `should_returnProducts_when_getProductsByIdWithValidItems` to stub
    `productRepository.findAllById(List.of(ID_1, ID_2))`.
  - `should_returnProductsInRequestOrder_when_repositoryReturnsUnordered` — repository returns
    `[p2, p1]`, request `[ID_1, ID_2]`, result `[dto1, dto2]`.
  - `should_throwNotFoundListingEveryMissingId_when_anyIdMissing` — request
    `[ID_1, MISSING, NONEXISTENT]`, repository returns `[p1]`; assert `NotFoundException` whose
    message contains both missing ids in request order.
  Run them; they fail against the current per-id fan-out.
- [x] **Step 2: Bound the request DTO**:

```java
public record ItemRequestDto(
        @NotEmpty(message = "items must not be empty")
        @Size(max = 100, message = "items must contain at most 100 ids")
        List<@NotNull UUID> items) {
}
```

  (`jakarta.validation.constraints.NotNull`, `.Size`; keep `@NotEmpty`.)
- [x] **Step 3: Add the single-query repository method**:

```java
@Override
@EntityGraph(attributePaths = "category")
List<Product> findAllById(Iterable<UUID> ids);
```

  Comments stay lazy — `@BatchSize(50)` on `Product.comments` yields at most two extra
  queries for 100 rows.
- [x] **Step 4: Rewrite `getProductsById`**:

```java
@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('SCOPE_catalog.read')")
public List<ProductResponseDto> getProductsById(ItemRequestDto product) {
    List<UUID> requested = product.items();
    Map<UUID, Product> found = productRepository.findAllById(requested).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    List<UUID> missing = requested.stream()
            .filter(id -> !found.containsKey(id))
            .distinct()
            .toList();
    if (!missing.isEmpty()) {
        throw new NotFoundException("Products", missing);
    }
    return requested.stream()
            .map(id -> convertProduct(found.get(id)))
            .toList();
}
```

  `@Transactional(readOnly = true)` is essential: mapping touches the lazy `comments`
  collection and `convertProduct` dereferences `category`. Duplicates in the request repeat in
  the response (map lookup). Remove the `VirtualThreads` import — this is its last use; leave
  the class itself for Task 6.2.
- [x] **Step 5: Add the MVC validation test** in `ProductControllerMvcTest`:
  `should_return400ProblemJson_when_batchGetExceeds100Ids` — build a JSON body with 101 UUIDs
  of the same value; expect 400 problem+json. `:batch-get` bypasses the idempotency guard, so
  no `Idempotency-Key` is needed. Optionally add a null-element case → 400.
- [x] **Step 6: Verify** unit + full verify.
- [x] **Step 7: Commit** — subject `fix(catalog): bound batch-get and load products in one query`.

**Done when:** 101 ids → 400; one or more missing ids → 404 naming every missing id in request
order; the service call issues exactly one `findAllById` (comments batched, category joined).

**Gotchas:** `findAllById` order is unspecified — never rely on it. `@EntityGraph` on the
override applies to all callers. A soft-deleted id is "missing" because `@SQLRestriction`
filters it — consistent with `findById`. Do not fetch comments with a collection join: it
would duplicate rows.

---

### Task 6: 1.6 — Compute average stars in SQL and validate the star range

**Files:**
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/repository/CommentRepository.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/service/comment/CommentServiceImpl.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/dto/comment/CommentRequestDto.java`
- Modify: `catalog/src/main/java/com/ganchevdimitarg/catalog/dto/comment/CreateCommentCommand.java`
- Create: `catalog/src/main/resources/db/migration/V10__comment_star_range_check.sql`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/comment/CommentServerImplTest.java`
- Modify: `catalog/src/test/java/com/ganchevdimitarg/catalog/comment/CommentControllerMvcTest.java`

**Interfaces:**
- Consumes: `ProductRepository.findByName` (already soft-delete filtered).
- Produces: `CommentRepository.findAverageStarByProductId(UUID): Double`; star validation on
  the request DTO and command; DB check constraint `comments_star_range`.

**Steps:**

- [x] **Step 1: Write the failing unit tests** in `CommentServerImplTest`:
  - `should_returnAverageStars_when_productExists` — stub `findByName("mouse")` → product
    with id, stub `findAverageStarByProductId(productId)` → `4.0`; assert `4.0` and verify no
    `findAllByProductName` call.
  - `should_returnZero_when_productHasNoComments` — repository returns `null`; assert `0.0`.
  - `should_returnZero_when_productDoesNotExist` — `findByName` empty; assert `0.0` (preserves
    current behaviour).
  Run them; they fail against the in-memory loop.
- [x] **Step 2: Replace the repository query** — delete `findAllByProductName` and add:

```java
@Query("select avg(c.star) from Comment c where c.product.id = :productId")
Double findAverageStarByProductId(@Param("productId") UUID productId);
```

  Query by **id**, not `c.product.name`: Hibernate does not reliably apply `@SQLRestriction`
  to implicit joins, and the product is already resolved through the filtered `findByName`.
  The entity query filters soft-deleted comments via `@SQLRestriction` automatically.
- [x] **Step 3: Rewrite `getAvgStars`**:

```java
@Override
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('SCOPE_catalog.read')")
public double getAvgStars(String productName) {
    return productRepository.findByName(productName)
            .map(product -> commentRepository.findAverageStarByProductId(product.getId()))
            .map(avg -> avg == null ? 0.0 : avg)
            .orElse(0.0);
}
```

  (The existing `@Transactional`/`@PreAuthorize` annotations stay.)
- [x] **Step 4: Validate the star range** — keep `double`, add to **both**
  `CommentRequestDto.star` and `CreateCommentCommand.star`:

```java
@DecimalMin(value = "1.0", message = "The star rating must be at least 1")
@DecimalMax(value = "5.0", message = "The star rating must be at most 5")
double star,
```

  (`jakarta.validation.constraints.DecimalMin` / `.DecimalMax`.) The controller validates the
  DTO (`@Valid`); the command constraints keep the command self-consistent for unit tests and
  future callers (the controller constructs the command manually, so command constraints are
  not enforced on the HTTP path).
- [x] **Step 5: Write the failing MVC validation tests** in `CommentControllerMvcTest`:
  - star `6` → 400 problem+json;
  - star `0` (or missing, which defaults to `0.0`) → 400;
  - star `4.5` → 201 (fractional ratings remain valid).
  Each write request needs a write-scope `jwt()` and an `Idempotency-Key`.
- [x] **Step 6: Add `V10__comment_star_range_check.sql`** — fail loudly, never clamp:

```sql
-- Pre-flight expectation: no rows outside 1..5. Values were validated only by the DTO
-- from now on; historical rows are checked here and the migration fails if any are invalid.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM comments WHERE star < 1 OR star > 5) THEN
        RAISE EXCEPTION
            'comments.star contains values outside 1..5; normalise them before applying V10';
    END IF;
END $$;

ALTER TABLE comments
    ADD CONSTRAINT comments_star_range CHECK (star BETWEEN 1 AND 5);
```

  Run the pre-flight query against the dev DB before committing; document the result in the
  PR/session notes.
- [x] **Step 7: Verify** unit + full verify (Flyway applies `V10`; `ddl-auto=validate` passes).
- [x] **Step 8: Commit** — subject `fix(catalog): compute average stars in SQL and validate star range`.

**Done when:** the average is one SQL aggregate (no comment list in memory), `null` and unknown
products return `0.0`, out-of-range stars are rejected with 400, and the DB rejects direct
out-of-range writes via `comments_star_range`.

**Gotchas:** `avg()` returns `Double` and is `null` when no rows match — never unbox blindly.
`@NotNull` on a primitive is meaningless. The existing MVC test that stubs
`getAvgStars("nonexistent")` to throw `NotFoundException` still passes because it mocks the
service; it does not describe real service behaviour and needs no change. Migration numbering:
`V9` is Task 2; if tasks are executed out of order, renumber so versions stay monotonic.

---

## Phase 1 close-out

- [x] Run the full suite one final time: unit + ITs + JaCoCo gates green.
- [x] Update the migration note in `catalog/CLAUDE.md` (`next free version (currently V9)` →
  `currently V11`) — separate small docs commit, subject confirmed with the user.
- [ ] Do **not** tick the TODO checkboxes until the branch is merged (TODO.md's own rule).
- [x] Record any non-obvious findings in the repo `MEMORY.md` under `## Solved problems`
  (format `- [YYYY-MM] catalog: <what was wrong> → <what fixed it>`).

---

## Verification

Run from the repo root in Git Bash after **every** task:

```bash
# Fast unit loop (no Docker)
./mvnw test -pl catalog -am -Dtest='!*IT' -Dsurefire.failIfNoSpecifiedTests=false

# Full verify — unit + Testcontainers ITs + JaCoCo (Docker Desktop required)
./mvnw clean verify -pl catalog -am
```

- Expected: `BUILD SUCCESS`, `All coverage checks have been met.` (BUNDLE line ≥ 0.85,
  `com.ganchevdimitarg.catalog.service.*` line = 1.00).
- If the root reactor blocks (known issue), install the shared client once and build catalog
  standalone:

```bash
./mvnw -f client/pom.xml install -DskipTests
./mvnw -f catalog/pom.xml clean verify
```

- Migration drift is checked automatically: ITs start Testcontainers Postgres with
  `spring.flyway.enabled=true` and `ddl-auto=validate`. There is no configured
  `flyway-maven-plugin`, so do not rely on `flyway:validate`.
- Inspect `catalog/target/surefire-reports/*.txt` for the per-class counts; the console
  `Results:` block is the summary.

**End-to-end checks after all six tasks:**

1. Write endpoint with only `SCOPE_catalog.read` → 403 `ACCESS_DENIED`.
2. Duplicate live name at the DB (bypassing the service check) → 409 `CONFLICT`.
3. Create → delete → recreate the same product/category name → succeeds.
4. Delete a category with products → 409; move products first → succeeds.
5. `getProductById` → `createComment`/`moveOneProduct` → `getProductById` hits the repository
   again (no stale cache).
6. Batch-get with 101 ids → 400; with a missing id → 404 naming it.
7. `getAvgStars` returns the SQL average; star `0`, `6` → 400; `4.5` → accepted.

---

## TODO fact-checks (do not re-litigate during execution)

- 1.1's "very likely swallows" is definite: `@ExceptionHandler(Exception.class)` wins over the
  security filter's handler because the exception is thrown from the service invocation.
- 1.2's "two live duplicates → 409" is true only at the HTTP layer **after** 1.1; at the
  persistence layer it is a `DataIntegrityViolationException`.
- 1.4's `deleteCategory` eviction is largely redundant after 1.3 (empty categories only);
  it is kept as cheap defensive invalidation.
- 6.3 is stale: `catalog/pom.xml` already binds JaCoCo `report` + `check` at `verify`
  (BUNDLE ≥ 0.85, `service.*` = 1.00).
- 1.6's alternative "switch to int 1–5" was rejected — keep `double` (user decision).
- `catalog/CLAUDE.md` and TODO now both say the next free migration is **V11**.

---

## Completion checklist (Phase 1 done)

- [x] 1.1 — 403/409 mapping green, including the full-context method-security IT.
- [x] 1.2 — `V9` applied; soft-deleted names reusable; live duplicates still rejected.
- [x] 1.3 — category delete without products succeeds; with products → 409; no cascade remains.
- [x] 1.4 — cache evicted after commit on comment creation and all three category operations.
- [x] 1.5 — batch-get bounded to 100, one query, ordered, all missing ids reported.
- [x] 1.6 — SQL average, `null`/unknown → 0.0, star range validated in DTO and DB (`V10`).
- [x] Six commits with the exact subjects in TODO.md, each verified green before committing.
- [x] No edits outside `catalog/`; no committed secrets; no edited `V1`–`V8` migrations.
