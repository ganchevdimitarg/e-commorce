# Catalog Grade-A Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the catalog module's `/review` audit score from 76/100 (B) to ≥95/100 (A) by landing every Warning and most Suggestions from the audit.

**Architecture:** Four sequenced phases, each leaving `./mvnw clean verify` green and independently committable. P1 introduces a three-role DTO split (request/command/response) and a RESTful resource API with id-keyed writes. P2 enriches Kafka events with ID-reference payloads + trace headers, fixes MDC trace context via Micrometer `Tracer`, makes idempotency mandatory on writes, and removes the auth hot-path double-decode. P3 fixes the bulk-move N+1 and tightens entity encapsulation. P4 hardens tests and raises the coverage gate to 85%.

**Tech Stack:** Java 25, Spring Boot 4.1.0 (WebMVC), Spring Data JPA + PostgreSQL + Flyway, MapStruct, spring-kafka (JSON), Spring Data Redis, Micrometer tracing bridge (OTLP), JUnit 5 + Mockito + AssertJ + Testcontainers + Awaitility.

## Global Constraints

- Build with `./mvnw` (Maven wrapper) — never bare `mvn`. Single-module: append `-pl catalog -am` from repo root, or run inside `catalog/`.
- Java 25 · Spring Boot 4.1.0 · WebMVC. No reactive types (`Mono`/`Flux`).
- Immutable DTOs/commands/responses/events are `record`s — never classes.
- `@Transactional` on the service layer only. `@PreAuthorize("hasAuthority('SCOPE_catalog.read'|'SCOPE_catalog.write')")` on service methods, never controllers.
- Repositories return `Optional<T>`; unwrap via `orElseThrow(...)` — never `Optional.get()` unguarded.
- Domain failures use the `BusinessException` hierarchy (`NotFoundException` 404, `ConflictException` 409, `ValidationException` 400). All errors → `application/problem+json` via the existing `ControllerExceptionHandler`. Never catch a `BusinessException` and rethrow as `RuntimeException`.
- `ddl-auto: validate` in every profile; `spring.flyway.enabled` never `false`. Never edit a committed migration — add a new `V<n>__<snake_case>.sql` (two underscores). New tables carry `created_at`/`updated_at`/`deleted_at`; soft-delete only.
- Lombok: `@Getter`/`@Setter`/`@NoArgsConstructor` on entities (nothing more); `@RequiredArgsConstructor` on components; `@Slf4j` for logging. Never `@Data`/`@ToString`/`@EqualsAndHashCode` on entities with associations.
- Jackson `NON_NULL` globally; ISO-8601 dates; no manual `ObjectMapper`.
- Test naming `should_<expectedBehavior>_when_<condition>`; `@Tag("unit")` / `@Tag("integration")`. Integration tests extend `AbstractIntegrationTest` (Postgres) or `RedisKafkaIntegrationBase` (Redis+Kafka) — never H2. No `Thread.sleep()` — use Awaitility.
- Coverage gate after P4: **85% line** overall, **100% line** on domain logic.
- British English in prose/comments. Conventional Commits (`type(catalog): subject`). End commit messages with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Stage explicit paths — never `git add -A`.
- Custom metric names `catalog.<entity>.<action>`. Kafka topics `catalog.product.{created,updated,deleted}`; JSON serialisation, no schema registry.

---

## File Structure

**Phase 1 — commands + API**
- Create `dto/product/CreateProductCommand.java`, `dto/product/UpdateProductCommand.java`, `dto/category/MoveProductCommand.java`, `dto/comment/CreateCommentCommand.java`, `dto/category/CreateCategoryCommand.java`
- Modify `mapper/MapStructMapper.java` (request→command, command→entity)
- Modify `service/product/ProductService.java` + `ProductServiceImpl.java` (command inputs, id-keyed writes, drop manual blank-checks, precise cache evict)
- Modify `service/category/CategoryService.java` + `CategoryServiceImpl.java`, `service/comment/CommentService.java` + `CommentServiceImpl.java`
- Rewrite `controller/ProductController.java`, `CategoryController.java`, `CommentController.java` (resource paths, 201/Location, 204)

**Phase 2 — events + observability**
- Modify `event/ProductEvent.java` (eventId/productId/occurredAt), `event/ProductEventPublisher.java` (headers, id threading), `config/KafkaProducerConfig.java` (unchanged serializer; verify)
- Modify `config/MdcRequestFilter.java` (`Tracer`), `idempotency/IdempotencyInterceptor.java` + `IdempotencyConfig.java` (required + path scope), `config/ResourceServerConfig.java` (`isJwt` discriminator)

**Phase 3 — perf + encapsulation**
- Modify `repository/ProductRepository.java` (`moveAllProductsToCategory`, `findByNameAndCategoryId`), `service/category/CategoryServiceImpl.java` (bulk move)
- Modify `domain/Product.java`, `domain/Category.java`, `domain/Comment.java` (field setters, copy getters, size align)
- Create `src/main/resources/db/migration/V7__alter_products_name_length.sql`

**Phase 4 — tests + gate**
- Modify all affected `*Test` classes; create `product/ProductControllerMvcTest` cases, `event/ProductEventIT` header assertions, `config/MdcRequestFilterTest`
- Modify `pom.xml` (JaCoCo line minimum `0.80` → `0.85`)

---

## Phase 1 — Domain commands + RESTful API

### Task 1: Command records

**Files:**
- Create: `src/main/java/com/concordeu/catalog/dto/product/CreateProductCommand.java`
- Create: `src/main/java/com/concordeu/catalog/dto/product/UpdateProductCommand.java`
- Create: `src/main/java/com/concordeu/catalog/dto/category/CreateCategoryCommand.java`
- Create: `src/main/java/com/concordeu/catalog/dto/category/MoveProductCommand.java`
- Create: `src/main/java/com/concordeu/catalog/dto/comment/CreateCommentCommand.java`
- Test: `src/test/java/com/concordeu/catalog/command/CommandValidationTest.java`

**Interfaces:**
- Produces:
  - `CreateProductCommand(String name, String description, BigDecimal price, boolean inStock, String characteristics, String categoryName)`
  - `UpdateProductCommand(String description, BigDecimal price, boolean inStock, String characteristics)`
  - `CreateCategoryCommand(String name)`
  - `MoveProductCommand(String categoryNameFrom, String categoryNameTo, String productName)`
  - `CreateCommentCommand(String title, String text, double star, String author, String productName)`

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.command;

import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class CommandValidationTest {

    @Test
    void should_buildCommand_when_priceNonNegative() {
        CreateProductCommand cmd =
                new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        assertThat(cmd.name()).isEqualTo("mouse");
    }

    @Test
    void should_throwValidation_when_priceNegative() {
        assertThatThrownBy(() ->
                new CreateProductCommand("mouse", "WiFi mouse USB", new BigDecimal("-1"), true, "", "PC"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=CommandValidationTest`
Expected: FAIL — `CreateProductCommand` does not exist (compile error).

- [ ] **Step 3: Write the command records**

`CreateProductCommand.java`:
```java
package com.concordeu.catalog.dto.product;

import com.concordeu.catalog.exception.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductCommand(
        @NotBlank @Size(min = 3, max = 20) String name,
        @NotBlank @Size(min = 10, max = 50) String description,
        @NotNull BigDecimal price,
        boolean inStock,
        String characteristics,
        @NotBlank String categoryName) {

    public CreateProductCommand {
        if (price != null && price.signum() < 0) {
            throw new ValidationException("price must not be negative");
        }
    }
}
```

`UpdateProductCommand.java`:
```java
package com.concordeu.catalog.dto.product;

import com.concordeu.catalog.exception.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductCommand(
        @NotBlank @Size(min = 10, max = 50) String description,
        @NotNull BigDecimal price,
        boolean inStock,
        String characteristics) {

    public UpdateProductCommand {
        if (price != null && price.signum() < 0) {
            throw new ValidationException("price must not be negative");
        }
    }
}
```

`CreateCategoryCommand.java`:
```java
package com.concordeu.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryCommand(@NotBlank @Size(min = 2, max = 200) String name) {
}
```

`MoveProductCommand.java`:
```java
package com.concordeu.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;

public record MoveProductCommand(
        @NotBlank String categoryNameFrom,
        @NotBlank String categoryNameTo,
        @NotBlank String productName) {
}
```

`CreateCommentCommand.java`:
```java
package com.concordeu.catalog.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentCommand(
        @NotBlank @Size(min = 3, max = 15) String title,
        @NotBlank @Size(min = 10, max = 150) String text,
        double star,
        @NotBlank String author,
        @NotBlank String productName) {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=CommandValidationTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/dto catalog/src/test/java/com/concordeu/catalog/command/CommandValidationTest.java
git commit -m "feat(catalog): add write command records with compact-constructor rules

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: MapStruct mappings for commands

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/mapper/MapStructMapper.java`
- Test: `src/test/java/com/concordeu/catalog/mapper/CommandMapperTest.java`

**Interfaces:**
- Consumes: command records from Task 1.
- Produces (added to `MapStructMapper`):
  - `CreateProductCommand mapProductRequestToCreateCommand(ProductRequestDto dto, String categoryName)`
  - `UpdateProductCommand mapProductRequestToUpdateCommand(ProductRequestDto dto)`
  - `Product mapCreateCommandToProduct(CreateProductCommand cmd)`
  - `CreateCommentCommand mapCommentRequestToCreateCommand(CommentRequestDto dto, String productName)`
  - `Comment mapCreateCommentCommandToComment(CreateCommentCommand cmd)`

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.mapper;

import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CommandMapperTest {

    private final MapStructMapper mapper = Mappers.getMapper(MapStructMapper.class);

    @Test
    void should_mapRequestAndCategory_toCreateCommand() {
        ProductRequestDto dto = new ProductRequestDto("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "rgb");

        CreateProductCommand cmd = mapper.mapProductRequestToCreateCommand(dto, "PC");

        assertThat(cmd.name()).isEqualTo("mouse");
        assertThat(cmd.categoryName()).isEqualTo("PC");
        assertThat(cmd.characteristics()).isEqualTo("rgb");
    }

    @Test
    void should_mapCreateCommand_toProductEntity() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "rgb", "PC");

        Product product = mapper.mapCreateCommandToProduct(cmd);

        assertThat(product.getName()).isEqualTo("mouse");
        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=CommandMapperTest`
Expected: FAIL — methods not defined on `MapStructMapper` (compile error).

- [ ] **Step 3: Add the mapper methods**

Add imports and methods to `MapStructMapper`:
```java
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import com.concordeu.catalog.dto.comment.CreateCommentCommand;
import org.mapstruct.Mapping;
```
```java
    CreateProductCommand mapProductRequestToCreateCommand(ProductRequestDto dto, String categoryName);
    UpdateProductCommand mapProductRequestToUpdateCommand(ProductRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Product mapCreateCommandToProduct(CreateProductCommand cmd);

    CreateCommentCommand mapCommentRequestToCreateCommand(CommentRequestDto dto, String productName);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    Comment mapCreateCommentCommandToComment(CreateCommentCommand cmd);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=CommandMapperTest`
Expected: PASS (2 tests). (MapStruct regenerates the impl during `test-compile`.)

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/mapper/MapStructMapper.java catalog/src/test/java/com/concordeu/catalog/mapper/CommandMapperTest.java
git commit -m "feat(catalog): map request DTOs to write commands and entities

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: ProductService — command inputs + id-keyed writes

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/service/product/ProductService.java`
- Modify: `src/main/java/com/concordeu/catalog/service/product/ProductServiceImpl.java`
- Modify: `src/main/java/com/concordeu/catalog/repository/ProductRepository.java`
- Test: `src/test/java/com/concordeu/catalog/product/ProductServiceImplTest.java` (migrate existing)

**Interfaces:**
- Consumes: `CreateProductCommand`, `UpdateProductCommand`, `mapCreateCommandToProduct`.
- Produces (new `ProductService` signatures):
  - `ProductResponseDto createProduct(CreateProductCommand command)`
  - `void updateProduct(String id, UpdateProductCommand command)`
  - `void deleteProduct(String id)`
  - unchanged: `getProductsByPage`, `getProductsByCategoryByPage`, `getProductByName`, `getProductById`, `getProductsById`
- Produces (new repository methods): `int updateById(String id, String description, BigDecimal price, String characteristics, boolean inStock, long version)`, `void deleteById(String id)` (inherited from `JpaRepository` — keep).

- [ ] **Step 1: Write the failing test** (replace the create/update/delete tests in `ProductServiceImplTest`)

```java
    @Test
    void should_createNewProduct_when_categoryExistsAndNameUnique() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Category category = new Category();
        category.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));
        Product product = new Product();
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);

        testService.createProduct(cmd);

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());
        assertThat(argument.getValue().isInStock()).isTrue();
        verify(productEventPublisher).publishCreated(product.getId(), product.getName());
    }

    @Test
    void should_updateProductById_when_productExists() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setVersion(1L);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(1);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        testService.updateProduct("p1", cmd);

        verify(productRepository).updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L);
        verify(productEventPublisher).publishUpdated("p1", existing.getName());
    }

    @Test
    void should_throwOptimisticLock_when_updateByIdVersionMismatch() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setVersion(1L);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(0);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct("p1", cmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_deleteProductById_when_productExists() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setName("mouse");
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));

        testService.deleteProduct("p1");

        verify(productRepository).delete(existing);
        verify(productEventPublisher).publishDeleted("p1", "mouse");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=ProductServiceImplTest`
Expected: FAIL — new signatures/repository method/publisher arity don't exist (compile error).

- [ ] **Step 3: Update the repository** — add to `ProductRepository`:

```java
    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.description = :description, p.price = :price, \
            p.characteristics = :characteristics, p.inStock = :inStock, \
            p.version = p.version + 1 \
            where p.id = :id and p.version = :version
            """)
    int updateById(@Param("id") String id, @Param("description") String description,
                   @Param("price") BigDecimal price, @Param("characteristics") String characteristics,
                   @Param("inStock") boolean inStock, @Param("version") long version);
```
(Keep the existing `update(...)`/`deleteByName` for now; they are removed in Task 6 cleanup if unused — verify with a usage search before deleting.)

- [ ] **Step 4: Update `ProductService` interface**

```java
package com.concordeu.catalog.service.product;

import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductResponseDto createProduct(CreateProductCommand command);
    Page<ProductResponseDto> getProductsByPage(Pageable pageable);
    Page<ProductResponseDto> getProductsByCategoryByPage(Pageable pageable, String categoryName);
    ProductResponseDto getProductByName(String name);
    ProductResponseDto getProductById(String id);
    void updateProduct(String id, UpdateProductCommand command);
    void deleteProduct(String id);
    List<ProductResponseDto> getProductsById(ItemRequestDto items);
}
```

- [ ] **Step 5: Update `ProductServiceImpl`** — replace `createProduct`, `updateProduct`, `deleteProduct`, and drop the manual blank-checks in `getProductByName`/`getProductById` (validation now lives on the controller params, Task 4):

```java
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public ProductResponseDto createProduct(CreateProductCommand command) {
        Category category = categoryRepository.findByName(command.categoryName())
                .orElseThrow(() -> {
                    log.warn("No such category: {}", command.categoryName());
                    return new NotFoundException("Category", command.categoryName());
                });

        if (productRepository.findByName(command.name()).isPresent()) {
            log.warn("Product with the name: {} already exists.", command.name());
            throw new ConflictException("Product with the name: " + command.name() + " already exist.");
        }

        Product product = mapper.mapCreateCommandToProduct(command);
        product.setCategory(category);

        productRepository.saveAndFlush(product);
        log.info("The product {} is save successful", product.getName());
        meterRegistry.counter("catalog.product.created").increment();
        publishAfterCommit(() -> productEventPublisher.publishCreated(product.getId(), product.getName()));

        return mapper.mapProductToProductResponseDto(product);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void updateProduct(String id, UpdateProductCommand command) {
        Product existing = findProductById(id);

        int updated = productRepository.updateById(id,
                command.description(), command.price(), command.characteristics(),
                command.inStock(), existing.getVersion());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), id);
        }
        meterRegistry.counter("catalog.product.updated").increment();
        publishAfterCommit(() -> productEventPublisher.publishUpdated(id, existing.getName()));
        log.info("The updates were successful on product: {}", id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @CacheEvict(cacheNames = "product", key = "#id")
    public void deleteProduct(String id) {
        Product existing = findProductById(id);
        productRepository.delete(existing);   // honours @SQLDelete soft-delete
        meterRegistry.counter("catalog.product.deleted").increment();
        publishAfterCommit(() -> productEventPublisher.publishDeleted(id, existing.getName()));
    }

    private Product findProductById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product with the id: {} does not exist.", id);
                    return new NotFoundException("Product", id);
                });
    }
```

Also remove the now-unused `if (name == null || name.isBlank())` / `if (id == null || id.isBlank())` guards at the top of `getProductByName`/`getProductById` (the controller enforces `@NotBlank`). Keep the `findByName(...).orElseThrow(...)` lookups. Remove the now-unused `findProductByName` only if no caller remains.

> NOTE: `publishCreated/publishUpdated/publishDeleted` change to two args `(id, name)` here — the publisher itself is updated in Phase 2 Task 8. Between Task 3 and Task 8 the build stays green because Task 8's publisher signatures are introduced as part of making this compile. To keep tasks independently green, **add the two-arg publisher overloads in this task** (delegating to the existing one-arg send temporarily), then Phase 2 Task 8 replaces the body with header-aware sending. Concretely, in this task add to `ProductEventPublisher`:
> ```java
> public void publishCreated(String productId, String productName) { send(CREATED, productName, new ProductEvent.ProductCreated(productName)); }
> public void publishUpdated(String productId, String productName) { send(UPDATED, productName, new ProductEvent.ProductUpdated(productName)); }
> public void publishDeleted(String productId, String productName) { send(DELETED, productName, new ProductEvent.ProductDeleted(productName)); }
> ```
> and delete the old one-arg methods. Task 8 enriches the event/headers.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=ProductServiceImplTest`
Expected: PASS (all migrated cases).

- [ ] **Step 7: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/service/product catalog/src/main/java/com/concordeu/catalog/repository/ProductRepository.java catalog/src/main/java/com/concordeu/catalog/event/ProductEventPublisher.java catalog/src/test/java/com/concordeu/catalog/product/ProductServiceImplTest.java
git commit -m "refactor(catalog): drive product writes by command + id, precise cache evict

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: ProductController — RESTful resource paths

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/controller/ProductController.java`
- Test: `src/test/java/com/concordeu/catalog/product/ProductControllerMvcTest.java` (migrate paths; full assertions in Task 16)

**Interfaces:**
- Consumes: `ProductService` (Task 3), `MapStructMapper` (Task 2).
- Produces: REST surface `POST /api/v1/catalog/products` (201+Location), `GET /products`, `GET /products/{id}`, `GET /products/by-name/{name}`, `PUT /products/{id}`, `DELETE /products/{id}` (204), `POST /products:batch-get`.

- [ ] **Step 1: Write the failing test** (one representative case here; suite expanded in Task 16)

```java
    @Test
    void should_return201AndLocation_when_createProduct() throws Exception {
        ProductResponseDto created = new ProductResponseDto("p1", "mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, java.util.List.of());
        when(productService.createProduct(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/catalog/products/p1"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=ProductControllerMvcTest`
Expected: FAIL — old path/method, no 201/Location.

- [ ] **Step 3: Rewrite `ProductController`**

```java
package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.PageResponse;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProductController {

    private final ProductService productService;
    private final MapStructMapper mapper;

    @Operation(summary = "Create product", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody @Valid ProductRequestDto requestDto,
                                                            @RequestParam @NotBlank String categoryName) {
        ProductResponseDto created =
                productService.createProduct(mapper.mapProductRequestToCreateCommand(requestDto, categoryName));
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + created.id())).body(created);
    }

    @Operation(summary = "List products", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    public PageResponse<ProductResponseDto> getProducts(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(productService.getProductsByPage(PageableSupport.capped(pageable)));
    }

    @Operation(summary = "List products by category", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping(params = "categoryName")
    public PageResponse<ProductResponseDto> getProductsByCategory(@PageableDefault(size = 20) Pageable pageable,
                                                                  @RequestParam @NotBlank String categoryName) {
        return PageResponse.of(productService.getProductsByCategoryByPage(PageableSupport.capped(pageable), categoryName));
    }

    @Operation(summary = "Get product by id", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/{id}")
    public ProductResponseDto getProductById(@PathVariable @NotBlank String id) {
        return productService.getProductById(id);
    }

    @Operation(summary = "Get product by name", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/by-name/{name}")
    public ProductResponseDto getProductByName(@PathVariable @NotBlank String name) {
        return productService.getProductByName(name);
    }

    @Operation(summary = "Batch get products by id", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping(":batch-get")
    public List<ProductResponseDto> getProductsById(@RequestBody @Valid ItemRequestDto items) {
        return productService.getProductsById(items);
    }

    @Operation(summary = "Update product", security = @SecurityRequirement(name = "security_auth"))
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable @NotBlank String id,
                                              @RequestBody @Valid ProductRequestDto requestDto) {
        productService.updateProduct(id, mapper.mapProductRequestToUpdateCommand(requestDto));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable @NotBlank String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=ProductControllerMvcTest`
Expected: PASS for the create case (others migrated in Task 16).

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/controller/ProductController.java catalog/src/test/java/com/concordeu/catalog/product/ProductControllerMvcTest.java
git commit -m "feat(catalog): RESTful product resource API with 201/Location and 204

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Category service + controller (commands, RESTful, dedup lookups)

**Files:**
- Modify: `service/category/CategoryService.java`, `CategoryServiceImpl.java`, `controller/CategoryController.java`
- Test: `category/CategoryServiceImplTest.java`, `category/CategoryControllerMvcTest.java`

**Interfaces:**
- Consumes: `CreateCategoryCommand`, `MoveProductCommand`.
- Produces:
  - `CategoryResponseDto createCategory(CreateCategoryCommand command)`
  - `void deleteCategory(String categoryName)` (unchanged)
  - `void moveOneProduct(MoveProductCommand command)`
  - `void moveAllProducts(String categoryNameFrom, String categoryNameTo)` (signature unchanged; body bulk-replaced in Task 12)
  - `getCategory`, `getCategoriesByPage` unchanged
  - Private helper `Category requireCategory(String name)` replacing the duplicated `getCategory(...)→DTO` lookups in move methods.

- [ ] **Step 1: Write the failing test** (migrate create + add helper coverage)

```java
    @Test
    void should_createCategory_when_nameUnique() {
        when(categoryRepository.findByName("PC")).thenReturn(Optional.empty());
        Category saved = new Category();
        saved.setId("c1");
        saved.setName("PC");
        when(categoryRepository.saveAndFlush(any())).thenReturn(saved);
        when(mapper.mapCategoryToCategoryResponseDto(saved))
                .thenReturn(new CategoryResponseDto("c1", "PC", java.util.List.of()));

        CategoryResponseDto result = testService.createCategory(new CreateCategoryCommand("PC"));

        assertThat(result.name()).isEqualTo("PC");
    }

    @Test
    void should_throwConflict_when_createCategoryDuplicate() {
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(new Category()));

        assertThatThrownBy(() -> testService.createCategory(new CreateCategoryCommand("PC")))
                .isInstanceOf(ConflictException.class);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=CategoryServiceImplTest`
Expected: FAIL — `createCategory(CreateCategoryCommand)` not defined.

- [ ] **Step 3: Update `CategoryService` interface**

```java
package com.concordeu.catalog.service.category;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.category.CreateCategoryCommand;
import com.concordeu.catalog.dto.category.MoveProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    CategoryResponseDto createCategory(CreateCategoryCommand command);
    CategoryResponseDto getCategory(String categoryName);
    void deleteCategory(String categoryName);
    void moveOneProduct(MoveProductCommand command);
    void moveAllProducts(String categoryNameFrom, String categoryNameTo);
    Page<CategoryResponseDto> getCategoriesByPage(Pageable pageable);
}
```

- [ ] **Step 4: Update `CategoryServiceImpl`** — replace `createCategory`, replace the empty-string guards with reliance on `@NotBlank` command/param validation, and introduce `requireCategory`:

```java
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CategoryResponseDto createCategory(CreateCategoryCommand command) {
        if (categoryRepository.findByName(command.name()).isPresent()) {
            log.warn("Category with the name: {} already exist.", command.name());
            throw new ConflictException("Category with the name: " + command.name() + " already exist.");
        }
        Category category = new Category();
        category.setName(command.name());
        category = categoryRepository.saveAndFlush(category);
        meterRegistry.counter("catalog.category.created").increment();
        return mapper.mapCategoryToCategoryResponseDto(category);
    }

    private Category requireCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Category", name));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveOneProduct(MoveProductCommand command) {
        Category from = requireCategory(command.categoryNameFrom());
        Category to = requireCategory(command.categoryNameTo());

        Product product = productRepository
                .findByNameAndCategoryId(command.productName(), from.getId())
                .orElseThrow(() -> {
                    log.warn("No such product: {}", command.productName());
                    return new NotFoundException("Product", command.productName());
                });

        int updated = productRepository.changeCategory(product.getName(), to.getId(), product.getVersion());
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Product.class.getSimpleName(), product.getName());
        }
        meterRegistry.counter("catalog.category.moved").increment();
    }
```

> `findByNameAndCategoryId` is added to `ProductRepository` in Task 12; for this task add the derived-query method signature now so the code compiles:
> ```java
> Optional<Product> findByNameAndCategoryId(String name, String categoryId);
> ```
> Leave `moveAllProducts` body as the existing per-row loop **but** rewritten to delegate to `moveOneProduct(new MoveProductCommand(from, to, name))`; Task 12 replaces it with the bulk UPDATE.

Delete `deleteCategory`'s `categoryName.isEmpty()` guard (controller `@NotBlank` covers it); keep the existence check.

- [ ] **Step 5: Rewrite `CategoryController`** to resource paths:

```java
package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.PageResponse;
import com.concordeu.catalog.dto.category.CategoryRequestDto;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.category.MoveProductCommand;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.category.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CategoryController {

    private final CategoryService categoryService;
    private final MapStructMapper mapper;

    @Operation(summary = "Create category", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(@RequestBody @Valid CategoryRequestDto requestDto) {
        CategoryResponseDto created = categoryService.createCategory(
                new com.concordeu.catalog.dto.category.CreateCategoryCommand(requestDto.name()));
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + created.name())).body(created);
    }

    @Operation(summary = "Delete category", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteCategory(@PathVariable @NotBlank String name) {
        categoryService.deleteCategory(name);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List categories", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    public PageResponse<CategoryResponseDto> getCategories(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(categoryService.getCategoriesByPage(PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Move one product between categories", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/{from}/products/{productName}:move")
    public ResponseEntity<Void> moveOneProduct(@PathVariable @NotBlank String from,
                                               @PathVariable @NotBlank String productName,
                                               @RequestParam @NotBlank String to) {
        categoryService.moveOneProduct(new MoveProductCommand(from, to, productName));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Move all products between categories", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/{from}/products:move-all")
    public ResponseEntity<Void> moveAllProducts(@PathVariable @NotBlank String from,
                                                @RequestParam @NotBlank String to) {
        categoryService.moveAllProducts(from, to);
        return ResponseEntity.ok().build();
    }
}
```

> `CategoryRequestDto` must expose `name()`. If it currently has a different component, check `dto/category/CategoryRequestDto.java` and adapt the constructor argument accordingly (do not change the record's public shape unless needed).

- [ ] **Step 6: Run tests**

Run: `./mvnw test -pl catalog -Dtest=CategoryServiceImplTest,CategoryControllerMvcTest`
Expected: PASS (migrated cases).

- [ ] **Step 7: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/service/category catalog/src/main/java/com/concordeu/catalog/controller/CategoryController.java catalog/src/main/java/com/concordeu/catalog/repository/ProductRepository.java catalog/src/test/java/com/concordeu/catalog/category
git commit -m "feat(catalog): RESTful category API, command inputs, dedup move lookups

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Comment service + controller (commands, RESTful, nested under product)

**Files:**
- Modify: `service/comment/CommentService.java`, `CommentServiceImpl.java`, `controller/CommentController.java`
- Test: `comment/CommentServerImplTest.java`, `comment/CommentControllerMvcTest.java`

**Interfaces:**
- Consumes: `CreateCommentCommand`, `mapCreateCommentCommandToComment`.
- Produces:
  - `CommentResponseDto createComment(CreateCommentCommand command)`
  - `findAllByProductNameByPage`, `findAllByAuthorByPage`, `getAvgStars` unchanged
- REST: `POST /api/v1/catalog/products/{name}/comments` (201+Location), `GET /products/{name}/comments`, `GET /comments/by-author/{author}`, `GET /products/{name}/comments/avg-stars`.

- [ ] **Step 1: Write the failing test** (migrate create)

```java
    @Test
    void should_createComment_when_productExists() {
        Product product = new Product();
        product.setName("mouse");
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));
        Comment comment = new Comment();
        comment.setTitle("nice");
        when(mapStructMapper.mapCreateCommentCommandToComment(any())).thenReturn(comment);
        when(mapStructMapper.mapCommentToCommentResponseDto(comment))
                .thenReturn(new CommentResponseDto("nice", "great product!!", 5.0, "joe", null));

        CommentResponseDto result = testService.createComment(
                new CreateCommentCommand("nice", "great product!!", 5.0, "joe", "mouse"));

        verify(commentRepository).saveAndFlush(comment);
        assertThat(result.title()).isEqualTo("nice");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=CommentServerImplTest`
Expected: FAIL — `createComment(CreateCommentCommand)` not defined.

- [ ] **Step 3: Update `CommentService` + impl**

Interface:
```java
    CommentResponseDto createComment(com.concordeu.catalog.dto.comment.CreateCommentCommand command);
```
Impl `createComment`:
```java
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public CommentResponseDto createComment(CreateCommentCommand command) {
        Product product = productRepository.findByName(command.productName())
                .orElseThrow(() -> {
                    logMessage(command.productName());
                    return new NotFoundException("Product", command.productName());
                });
        Comment comment = mapper.mapCreateCommentCommandToComment(command);
        comment.setProduct(product);
        commentRepository.saveAndFlush(comment);
        meterRegistry.counter("catalog.comment.created").increment();
        log.info("The comment {} is save successful", comment.getTitle());
        return mapper.mapCommentToCommentResponseDto(comment);
    }
```
Drop the `productName.isEmpty()`/`author.isEmpty()` guards (controller `@NotBlank` covers them); keep the `orElseThrow` lookups.

- [ ] **Step 4: Rewrite `CommentController`**

```java
package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.PageResponse;
import com.concordeu.catalog.dto.comment.CommentRequestDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;
import com.concordeu.catalog.dto.comment.CreateCommentCommand;
import com.concordeu.catalog.service.comment.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Create comment", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping("/products/{productName}/comments")
    public ResponseEntity<CommentResponseDto> createComment(@PathVariable @NotBlank String productName,
                                                            @RequestBody @Valid CommentRequestDto requestDto) {
        CommentResponseDto created = commentService.createComment(new CreateCommentCommand(
                requestDto.title(), requestDto.text(), requestDto.star(), requestDto.author(), productName));
        return ResponseEntity.created(
                URI.create("/api/v1/catalog/products/" + productName + "/comments")).body(created);
    }

    @Operation(summary = "List comments by product", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/products/{productName}/comments")
    public PageResponse<CommentResponseDto> findAllByProductName(@PathVariable @NotBlank String productName,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(commentService.findAllByProductNameByPage(productName, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "List comments by author", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/comments/by-author/{author}")
    public PageResponse<CommentResponseDto> findAllByAuthor(@PathVariable @NotBlank String author,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(commentService.findAllByAuthorByPage(author, PageableSupport.capped(pageable)));
    }

    @Operation(summary = "Average stars for product", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/products/{productName}/comments/avg-stars")
    public double getAvgStars(@PathVariable @NotBlank String productName) {
        return commentService.getAvgStars(productName);
    }
}
```

- [ ] **Step 5: Run tests + full module build**

Run: `./mvnw clean verify -pl catalog -am`
Expected: BUILD SUCCESS (Phase 1 complete, all suites green, coverage ≥ existing gate).

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/service/comment catalog/src/main/java/com/concordeu/catalog/controller/CommentController.java catalog/src/test/java/com/concordeu/catalog/comment
git commit -m "feat(catalog): RESTful comment API nested under product, command inputs

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 2 — Events + observability

### Task 7: Enrich the ProductEvent schema

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/event/ProductEvent.java`
- Test: `src/test/java/com/concordeu/catalog/event/ProductEventTest.java` (create)

**Interfaces:**
- Produces: sealed `ProductEvent` with `eventId()`, `productId()`, `productName()`, `occurredAt()`; subtypes `ProductCreated`/`ProductUpdated`/`ProductDeleted` each `(String eventId, String productId, String productName, Instant occurredAt)`.

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.event;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProductEventTest {

    @Test
    void should_exposeIdentityFields_onCreatedEvent() {
        Instant now = Instant.now();
        ProductEvent event = new ProductEvent.ProductCreated("e1", "p1", "mouse", now);

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(event.productId()).isEqualTo("p1");
        assertThat(event.productName()).isEqualTo("mouse");
        assertThat(event.occurredAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=ProductEventTest`
Expected: FAIL — constructor arity mismatch.

- [ ] **Step 3: Rewrite `ProductEvent`**

```java
package com.concordeu.catalog.event;

import java.time.Instant;

public sealed interface ProductEvent
        permits ProductEvent.ProductCreated, ProductEvent.ProductUpdated, ProductEvent.ProductDeleted {

    String eventId();
    String productId();
    String productName();
    Instant occurredAt();

    record ProductCreated(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
    record ProductUpdated(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
    record ProductDeleted(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=ProductEventTest`
Expected: PASS. (`ProductEventPublisher` will not compile yet — fixed in Task 8; do not run a full build between Task 7 and Task 8.)

- [ ] **Step 5: Commit** (with Task 8 — these two are a single compiling unit; commit at the end of Task 8.)

---

### Task 8: Publisher — headers + id threading

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/event/ProductEventPublisher.java`
- Test: `src/test/java/com/concordeu/catalog/event/ProductEventPublisherTest.java`

**Interfaces:**
- Consumes: enriched `ProductEvent` (Task 7), MDC `traceId`.
- Produces: `publishCreated/publishUpdated/publishDeleted(String productId, String productName)` sending a `ProducerRecord` with headers `traceId`, `correlationId` (=eventId), `eventType`.

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProductEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ProductEvent> template = mock(KafkaTemplate.class);

    @Test
    void should_sendRecordWithTraceAndCorrelationHeaders() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

        publisher.publishCreated("p1", "mouse");

        ArgumentCaptor<ProducerRecord<String, ProductEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, ProductEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("catalog.product.created");
        assertThat(record.key()).isEqualTo("p1");
        assertThat(record.value().productName()).isEqualTo("mouse");
        assertThat(header(record, "eventType")).isEqualTo("created");
        assertThat(header(record, "correlationId")).isEqualTo(record.value().eventId());
        assertThat(record.headers().lastHeader("traceId")).isNotNull();
    }

    private static String header(ProducerRecord<?, ?> record, String key) {
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=ProductEventPublisherTest`
Expected: FAIL — publisher still sends `(topic,key,value)` without headers / wrong arity.

- [ ] **Step 3: Rewrite `ProductEventPublisher`**

```java
package com.concordeu.catalog.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private static final String CREATED = "catalog.product.created";
    private static final String UPDATED = "catalog.product.updated";
    private static final String DELETED = "catalog.product.deleted";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishCreated(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductCreated(newEventId(), productId, productName, Instant.now());
        send(CREATED, "created", productId, event);
    }

    public void publishUpdated(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductUpdated(newEventId(), productId, productName, Instant.now());
        send(UPDATED, "updated", productId, event);
    }

    public void publishDeleted(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductDeleted(newEventId(), productId, productName, Instant.now());
        send(DELETED, "deleted", productId, event);
    }

    private void send(String topic, String eventType, String key, ProductEvent event) {
        ProducerRecord<String, ProductEvent> record = new ProducerRecord<>(topic, key, event);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", event.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("traceId", traceId().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event to {}: {}", topic, ex.getMessage(), ex);
                meterRegistry.counter("catalog.event.send.failed", "topic", topic).increment();
            }
        });
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=ProductEventPublisherTest,ProductEventTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/event catalog/src/test/java/com/concordeu/catalog/event/ProductEventTest.java catalog/src/test/java/com/concordeu/catalog/event/ProductEventPublisherTest.java
git commit -m "feat(catalog): ID-reference product events with trace/correlation headers

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: MDC trace context via Micrometer Tracer

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/config/MdcRequestFilter.java`
- Test: `src/test/java/com/concordeu/catalog/config/MdcRequestFilterTest.java`

**Interfaces:**
- Consumes: `io.micrometer.tracing.Tracer` (provided by `micrometer-tracing-bridge-otel`, already on the classpath).
- Produces: filter sets MDC `traceId`+`spanId` from the current span, `userId` from `X-User-Id`, `serviceId`; clears MDC on exit.

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class MdcRequestFilterTest {

    @Test
    void should_populateTraceAndSpanId_fromCurrentSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        MdcRequestFilter filter = new MdcRequestFilter(tracer);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("user-9");
        HttpServletResponse response = mock(HttpServletResponse.class);

        String[] captured = new String[3];
        FilterChain chain = (req, res) -> {
            captured[0] = MDC.get("traceId");
            captured[1] = MDC.get("spanId");
            captured[2] = MDC.get("userId");
        };

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("trace-123");
        assertThat(captured[1]).isEqualTo("span-456");
        assertThat(captured[2]).isEqualTo("user-9");
        assertThat(MDC.get("traceId")).isNull();   // cleared on exit
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=MdcRequestFilterTest`
Expected: FAIL — `MdcRequestFilter` has no `Tracer` constructor; no `spanId`.

- [ ] **Step 3: Rewrite `MdcRequestFilter`**

```java
package com.concordeu.catalog.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String SERVICE_ID = "catalog-service";

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Span span = tracer.currentSpan();
            if (span != null) {
                MDC.put("traceId", span.context().traceId());
                MDC.put("spanId", span.context().spanId());
            }
            MDC.put("userId", headerOrEmpty(request, "X-User-Id"));
            MDC.put("serviceId", SERVICE_ID);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String headerOrEmpty(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null ? value : "";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=MdcRequestFilterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/config/MdcRequestFilter.java catalog/src/test/java/com/concordeu/catalog/config/MdcRequestFilterTest.java
git commit -m "fix(catalog): derive MDC traceId/spanId from current span, not raw header

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 10: Idempotency required on writes, scoped to mutation paths

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/idempotency/IdempotencyInterceptor.java`
- Modify: `src/main/java/com/concordeu/catalog/idempotency/IdempotencyConfig.java`
- Test: `src/test/java/com/concordeu/catalog/idempotency/IdempotencyInterceptorTest.java`

**Interfaces:**
- Produces: missing/blank `Idempotency-Key` on a guarded write → `ValidationException` (400). The interceptor is registered only on write paths (excludes `:batch-get`). Present key → `SETNX`, duplicate → `ConflictException` (409).

- [ ] **Step 1: Write the failing test** (add cases)

```java
    @Test
    void should_throwValidation_when_writeMissingIdempotencyKey() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/catalog/products");
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void should_allowBatchGet_withoutIdempotencyKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/catalog/products:batch-get");
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=IdempotencyInterceptorTest`
Expected: FAIL — current code returns `true` on missing key.

- [ ] **Step 3: Update `IdempotencyInterceptor`**

```java
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!GUARDED.contains(request.getMethod()) || isReadViaPost(request)) {
            return true;
        }
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required for write requests");
        }
        Boolean firstSeen = redis.opsForValue().setIfAbsent("catalog:idempotency:" + key, "1", TTL);
        if (Boolean.FALSE.equals(firstSeen)) {
            log.warn("Duplicate idempotency key: {}", key);
            throw new ConflictException("Duplicate request for Idempotency-Key: " + key);
        }
        return true;
    }

    private boolean isReadViaPost(HttpServletRequest request) {
        return request.getRequestURI().endsWith(":batch-get");
    }
```
Add import `com.concordeu.catalog.exception.ValidationException;`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=IdempotencyInterceptorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/idempotency catalog/src/test/java/com/concordeu/catalog/idempotency/IdempotencyInterceptorTest.java
git commit -m "feat(catalog): require Idempotency-Key on writes, exclude read-via-POST

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 11: Auth hot-path — cheap JWT discriminator

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/config/ResourceServerConfig.java`
- Test: `src/test/java/com/concordeu/catalog/config/ResourceServerConfigTest.java`

**Interfaces:**
- Produces: `isJwt(HttpServletRequest)` decides JWT vs opaque by token **structure** (three base64url dot-segments with a decodable JSON header), without calling `jwtDecoder.decode`.

- [ ] **Step 1: Write the failing test** (assert no decode happens)

```java
    @Test
    void should_routeToJwt_withoutDecoding_when_tokenIsThreeSegmentJose() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // header: {"alg":"none"} base64url = eyJhbGciOiJub25lIn0
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.sig");

        boolean result = config.isJwt(request);

        assertThat(result).isTrue();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void should_routeToOpaque_when_tokenIsNotJose() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer opaque-abc123");

        assertThat(config.isJwt(request)).isFalse();
        verifyNoInteractions(jwtDecoder);
    }
```
(Ensure `jwtDecoder` is a Mockito `@Mock` injected into `config` in this test class.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=ResourceServerConfigTest`
Expected: FAIL — current `isJwt` calls `jwtDecoder.decode`, so `verifyNoInteractions` fails.

- [ ] **Step 3: Replace `isJwt`**

```java
    boolean isJwt(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : authorization.trim();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;   // opaque
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            return header.startsWith("{") && header.contains("alg");
        } catch (IllegalArgumentException e) {
            log.debug("Authorization token is not a JOSE header: {}", e.getMessage());
            return false;
        }
    }
```
Add imports `java.nio.charset.StandardCharsets;` and `java.util.Base64;`. Remove the now-unused `BadJwtException` import if no longer referenced.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=ResourceServerConfigTest`
Expected: PASS.

- [ ] **Step 5: Full Phase 2 build**

Run: `./mvnw clean verify -pl catalog -am`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/config/ResourceServerConfig.java catalog/src/test/java/com/concordeu/catalog/config/ResourceServerConfigTest.java
git commit -m "perf(catalog): route JWT vs opaque by token structure, drop double-decode

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 3 — Performance + encapsulation

### Task 12: Bulk category move

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/repository/ProductRepository.java`
- Modify: `src/main/java/com/concordeu/catalog/service/category/CategoryServiceImpl.java`
- Test: `src/test/java/com/concordeu/catalog/category/CategoryServiceImplTest.java`

**Interfaces:**
- Produces: `int moveAllProductsToCategory(String fromCategoryId, String toCategoryId)` on the repository; `moveAllProducts` issues exactly one bulk UPDATE.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void should_issueSingleBulkUpdate_when_moveAllProducts() {
        Category from = new Category();
        from.setId("from-1");
        from.setName("PC");
        Category to = new Category();
        to.setId("to-1");
        to.setName("Laptop");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(from));
        when(categoryRepository.findByName("Laptop")).thenReturn(Optional.of(to));
        when(productRepository.moveAllProductsToCategory("from-1", "to-1")).thenReturn(3);

        testService.moveAllProducts("PC", "Laptop");

        verify(productRepository, times(1)).moveAllProductsToCategory("from-1", "to-1");
        verify(productRepository, never()).changeCategory(anyString(), anyString(), anyLong());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=CategoryServiceImplTest`
Expected: FAIL — `moveAllProductsToCategory` not defined; current impl loops `changeCategory`.

- [ ] **Step 3: Add the repository bulk update**

```java
    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.category.id = :toId, p.version = p.version + 1 \
            where p.category.id = :fromId
            """)
    int moveAllProductsToCategory(@Param("fromId") String fromId, @Param("toId") String toId);
```
Also confirm `findByNameAndCategoryId(String name, String categoryId)` exists (added in Task 5); if not, add it as a derived query.

- [ ] **Step 4: Replace `moveAllProducts`**

```java
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void moveAllProducts(String categoryNameFrom, String categoryNameTo) {
        Category from = requireCategory(categoryNameFrom);
        Category to = requireCategory(categoryNameTo);

        int moved = productRepository.moveAllProductsToCategory(from.getId(), to.getId());
        meterRegistry.counter("catalog.category.moved").increment(moved);
        log.info("Moved {} products from {} to {}", moved, categoryNameFrom, categoryNameTo);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -pl catalog -Dtest=CategoryServiceImplTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/repository/ProductRepository.java catalog/src/main/java/com/concordeu/catalog/service/category/CategoryServiceImpl.java catalog/src/test/java/com/concordeu/catalog/category/CategoryServiceImplTest.java
git commit -m "perf(catalog): move all products via single bulk UPDATE, drop N+1 loop

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 13: Entity encapsulation + size/column alignment

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/domain/Product.java`
- Modify: `src/main/java/com/concordeu/catalog/domain/Category.java`
- Modify: `src/main/java/com/concordeu/catalog/domain/Comment.java`
- Create: `src/main/resources/db/migration/V7__alter_products_name_length.sql`
- Test: `src/test/java/com/concordeu/catalog/domain/EntityEncapsulationTest.java`

**Interfaces:**
- Produces: collection getters return defensive copies; field-level `@Setter` only on mutable business fields. `products.name` column narrowed to `varchar(20)` to match `@Size(min=3,max=20)`.

- [ ] **Step 1: Write the failing test**

```java
package com.concordeu.catalog.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class EntityEncapsulationTest {

    @Test
    void should_returnDefensiveCopy_fromCategoryProducts() {
        Category category = new Category();
        List<Product> backing = new ArrayList<>();
        backing.add(new Product());
        category.setProducts(backing);

        List<Product> exposed = category.getProducts();

        assertThatThrownBy(() -> exposed.add(new Product()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(category.getProducts()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl catalog -Dtest=EntityEncapsulationTest`
Expected: FAIL — live list is mutable.

- [ ] **Step 3: Update entities**

`Category` — remove class-level `@Setter`, add field-level setters on `name`/`products`, override the products getter:
```java
@NoArgsConstructor
@Getter
public class Category extends Auditable {
    // ... id (no setter — generated) ...
    @Setter
    @Column(name = "name", unique = true, nullable = false, length = 200)
    private String name;

    @Setter
    @OneToMany(mappedBy = "category", targetEntity = Product.class, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Product> products;

    public List<Product> getProducts() {
        return products == null ? List.of() : List.copyOf(products);
    }
}
```
Add `import lombok.Setter;` (drop the wildcard `lombok.*` if it pulled in `@Setter` at class level). Apply the equivalent pattern to `Product` (field setters on `name`, `description`, `price`, `inStock`, `characteristics`, `category`; `getComments()` returns `List.copyOf`) and `Comment` (field setters on business fields; no collection getter to wrap). Leave `id` without a setter (generated). Align `Product.name` column to `length = 20`.

- [ ] **Step 4: Create the Flyway migration** `V7__alter_products_name_length.sql`

```sql
-- Narrow products.name to match the @Size(min=3,max=20) business rule.
-- Existing rows were inserted under the same @Size cap, so no truncation occurs.
ALTER TABLE IF EXISTS products
    ALTER COLUMN name TYPE varchar(20);
```

- [ ] **Step 5: Run test + Flyway validate**

Run: `./mvnw test -pl catalog -Dtest=EntityEncapsulationTest`
Expected: PASS.
Run: `./mvnw flyway:validate -pl catalog`
Expected: No migration drift (or the documented "pending V7" if validate runs pre-migrate; the integration suite applies it).

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/domain catalog/src/main/resources/db/migration/V7__alter_products_name_length.sql catalog/src/test/java/com/concordeu/catalog/domain/EntityEncapsulationTest.java
git commit -m "refactor(catalog): narrow entity setters, copy collection getters, align name length

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 14: Native query → JPQL + dead-code sweep

**Files:**
- Modify: `src/main/java/com/concordeu/catalog/repository/ProductRepository.java`
- Test: `src/test/java/com/concordeu/catalog/persistence/CatalogPersistenceIT.java` (add assertion)

**Interfaces:**
- Produces: `findAllByCategoryIdByPage` as a JPQL query (so `@SQLRestriction` applies automatically); removal of any now-unused `update(...)`/`deleteByName(...)` after a usage check.

- [ ] **Step 1: Write/extend the failing IT** — assert soft-deleted products are excluded from `findAllByCategoryIdByPage`:

```java
    @Test
    void should_excludeSoftDeleted_when_findAllByCategoryIdByPage() {
        // given a category with one active and one soft-deleted product (persist + soft delete)
        // when findAllByCategoryIdByPage(categoryId, PageRequest.of(0, 10))
        // then result contains only the active product
        // (use the existing fixtures/helpers in CatalogPersistenceIT)
    }
```
Implement the body using the IT's existing persistence helpers (persist a category + two products, soft-delete one via `productRepository.delete(...)`, flush/clear, query).

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw verify -pl catalog -am -Dtest=CatalogPersistenceIT -Dgroups=integration`
Expected: FAIL — native `SELECT *` with the manual `deleted_at IS NULL` already filters, so this should actually PASS; if it passes, the assertion still guards the JPQL rewrite. (If it passes immediately, proceed — the rewrite must keep it green.)

- [ ] **Step 3: Rewrite the query** to JPQL:

```java
    @Query("select p from Product p where p.category.id = :categoryId")
    Page<Product> findAllByCategoryIdByPage(@Param("categoryId") String categoryId, Pageable pageable);
```
Remove the `nativeQuery = true` variant and its unused imports if any. Then grep for `update(` and `deleteByName(` usages; if unused after Phase 1, delete them:

Run: `rg -n "\.update\(|deleteByName" catalog/src/main/java`
Remove the methods only if no production caller remains.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw verify -pl catalog -am -Dtest=CatalogPersistenceIT -Dgroups=integration`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/java/com/concordeu/catalog/repository/ProductRepository.java catalog/src/test/java/com/concordeu/catalog/persistence/CatalogPersistenceIT.java
git commit -m "refactor(catalog): JPQL category query honours soft-delete, drop dead finders

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 4 — Test hardening + coverage gate

### Task 15: Strengthen unit assertions + command validation cases

**Files:**
- Modify: `src/test/java/com/concordeu/catalog/product/ProductServiceImplTest.java`
- Modify: `src/test/java/com/concordeu/catalog/product/ProductValidationTest.java`

**Interfaces:** consumes all Phase 1–3 production code.

- [ ] **Step 1: Strengthen the weak page assertion** — replace `should_returnProductsPage_when_pageRequested` to assert mapped content, not just interaction:

```java
    @Test
    void should_returnMappedProductsPage_when_pageRequested() {
        PageRequest pageRequest = PageRequest.of(0, 2);
        Product p = new Product();
        p.setId("p1");
        p.setName("mouse");
        p.setCategory(new Category());
        Page<Product> page = new PageImpl<>(List.of(p), pageRequest, 1);
        when(productRepository.findAll(pageRequest)).thenReturn(page);

        Page<ProductResponseDto> result = testService.getProductsByPage(pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("mouse");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
```

- [ ] **Step 2: Add command-validation unit cases** to `ProductValidationTest` (negative price, blank name, short description) asserting `ValidationException`/constraint failures.

- [ ] **Step 3: Run**

Run: `./mvnw test -pl catalog -Dtest=ProductServiceImplTest,ProductValidationTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add catalog/src/test/java/com/concordeu/catalog/product
git commit -m "test(catalog): assert mapped page content and command validation rules

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 16: MockMvc web-slice suite for the RESTful API

**Files:**
- Modify: `src/test/java/com/concordeu/catalog/product/ProductControllerMvcTest.java`
- Modify: `src/test/java/com/concordeu/catalog/category/CategoryControllerMvcTest.java`
- Modify: `src/test/java/com/concordeu/catalog/comment/CommentControllerMvcTest.java`

**Interfaces:** consumes the controllers from Tasks 4–6.

- [ ] **Step 1: Add the failing cases** — for products: 200 list, 200 get-by-id, 204 delete, 400 when `Idempotency-Key` missing on POST, 404 problem+json when service throws `NotFoundException`. Example delete + missing-key:

```java
    @Test
    void should_return204_when_deleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/products/p1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.write")))
                        .header("Idempotency-Key", "k-del"))
                .andExpect(status().isNoContent());
        verify(productService).deleteProduct("p1");
    }

    @Test
    void should_return400_when_createWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
```
> The MVC slice must register `IdempotencyInterceptor`; if the existing slice config excludes interceptors, import `IdempotencyConfig` (with a mocked `StringRedisTemplate` bean) or test the missing-key path through `@SpringBootTest`. Follow whichever pattern the existing `*MvcTest` classes already use for interceptors.

- [ ] **Step 2: Run to verify failures, then green**

Run: `./mvnw test -pl catalog -Dtest=ProductControllerMvcTest,CategoryControllerMvcTest,CommentControllerMvcTest`
Expected: FAIL first (new assertions), then PASS once paths/interceptor wiring align.

- [ ] **Step 3: Commit**

```bash
git add catalog/src/test/java/com/concordeu/catalog/product/ProductControllerMvcTest.java catalog/src/test/java/com/concordeu/catalog/category/CategoryControllerMvcTest.java catalog/src/test/java/com/concordeu/catalog/comment/CommentControllerMvcTest.java
git commit -m "test(catalog): web-slice coverage for RESTful API, 201/204/400 paths

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 17: Event integration test — payload + headers

**Files:**
- Modify: `src/test/java/com/concordeu/catalog/event/ProductEventIT.java`

**Interfaces:** consumes `ProductEventPublisher` (Task 8) over a real Kafka container (`RedisKafkaIntegrationBase`).

- [ ] **Step 1: Add the failing assertion** — consume the created event and assert payload + headers:

```java
    @Test
    void should_publishEventWithHeaders_when_productCreated() {
        // given: create a product through the service (write scope)
        // when: the afterCommit publish fires
        // then: a record on catalog.product.created carries productId/eventId/occurredAt
        //       and headers eventType=created, correlationId=eventId, traceId present
        await().atMost(10, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            ConsumerRecord<String, ProductEvent> record = pollSingle();   // existing helper or KafkaTestUtils
            assertThat(record.value().productName()).isEqualTo("mouse");
            assertThat(record.value().productId()).isNotBlank();
            assertThat(new String(record.headers().lastHeader("eventType").value(), UTF_8)).isEqualTo("created");
            assertThat(record.headers().lastHeader("correlationId")).isNotNull();
            assertThat(record.headers().lastHeader("traceId")).isNotNull();
        });
    }
```
Implement using the consumer/fixtures already present in `ProductEventIT` (adapt deserialiser to the enriched record).

- [ ] **Step 2: Run**

Run: `./mvnw verify -pl catalog -am -Dtest=ProductEventIT -Dgroups=integration`
Expected: PASS (after first observing the new assertion fail against pre-Task-8 code — it won't, since Task 8 is merged; the assertion guards regressions).

- [ ] **Step 3: Commit**

```bash
git add catalog/src/test/java/com/concordeu/catalog/event/ProductEventIT.java
git commit -m "test(catalog): assert event payload identity and trace/correlation headers

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 18: Raise the JaCoCo line gate to 85%

**Files:**
- Modify: `catalog/pom.xml` (the bundle/overall `LINE` limit `minimum` `0.80` → `0.85`)

**Interfaces:** none.

- [ ] **Step 1: Edit the gate** — in `pom.xml`, the first `LINE`/`COVEREDRATIO` limit (currently `<minimum>0.80</minimum>`, around line 290) becomes:

```xml
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.85</minimum>
                                        </limit>
```
Leave the domain-logic rule at `1.00`.

- [ ] **Step 2: Run the full gated build**

Run: `./mvnw clean verify -pl catalog -am`
Expected: BUILD SUCCESS with `jacoco:check` passing at 85%. If it fails on a specific package, add the missing unit tests there (config/holder classes) until the gate clears — do not lower the gate.

- [ ] **Step 3: Commit**

```bash
git add catalog/pom.xml
git commit -m "build(catalog): raise JaCoCo line coverage gate to 85%

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 4: Final verification — re-audit**

Run `/review` in Audit mode against the catalog module and confirm the scorecard is ≥95 with no Critical/Warning items. If any Warning remains, address it before declaring done.

---

## Self-Review notes (coverage of spec → tasks)

- Spec §2 commands + RESTful API → Tasks 1–6. ✓
- Spec §2 id-keyed writes + precise cache evict → Task 3. ✓
- Spec §2 validation cleanup (`@NotBlank` on params) → Tasks 4–6. ✓
- Spec §3 event schema + headers → Tasks 7–8. ✓
- Spec §3 trace correctness (spanId, parsed traceId) → Task 9. ✓
- Spec §3 idempotency required + read-via-POST exclusion → Task 10. ✓
- Spec §3 auth double-decode → Task 11. ✓
- Spec §4 bulk move → Task 12. ✓
- Spec §4 encapsulation + size/column align → Task 13. ✓
- Spec §4 batch-get OpenAPI (`@Operation`) → Task 4 (added inline); native→JPQL → Task 14. ✓
- Spec §5 test hardening + 85% gate → Tasks 15–18. ✓
- Spec §6 out-of-scope respected (no Mongo/Avro, no other modules). ✓
```
