package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.dto.PageResponse;
import com.ganchevdimitarg.catalog.dto.product.CursorPageResponse;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductRequestDto;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProductController {

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of("name", "price", "inStock", "createdAt", "updatedAt");

    private final ProductService productService;
    private final MapStructMapper mapper;

    @Operation(summary = "Create product", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody @Valid ProductRequestDto requestDto,
                                                            @RequestParam @NotBlank String categoryName) {
        ProductResponseDto created =
                productService.createProduct(mapper.mapProductRequestToCreateCommand(requestDto, categoryName));
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + created.id()))
                .header(HttpHeaders.ETAG, etag(created.version()))
                .body(created);
    }

    @Operation(summary = "List and search products", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping
    public PageResponse<ProductResponseDto> getProducts(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) @Size(max = 200)
            @Pattern(regexp = ".*\\S.*", message = "categoryName must not be blank") String categoryName) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(q, minPrice, maxPrice, inStock, categoryName);
        return PageResponse.of(productService.searchProducts(criteria,
                PageableSupport.capped(pageable, SORTABLE_PROPERTIES)));
    }

    @Operation(summary = "List and search products by cursor", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping(":cursor")
    public CursorPageResponse<ProductResponseDto> getProductsCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) @Size(max = 200)
            @Pattern(regexp = ".*\\S.*", message = "categoryName must not be blank") String categoryName) {
        String direction = cursorDirection(sort);
        ProductSearchCriteria criteria = new ProductSearchCriteria(q, minPrice, maxPrice, inStock, categoryName);
        return productService.searchProductsByCursor(criteria, cursor, limit, direction);
    }

    @Operation(summary = "Get product by id", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable UUID id) {
        ProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag(product.version()))
                .body(product);
    }

    @Operation(summary = "Get product by name", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/by-name/{name}")
    public ResponseEntity<ProductResponseDto> getProductByName(@PathVariable @NotBlank String name) {
        ProductResponseDto product = productService.getProductByName(name);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag(product.version()))
                .body(product);
    }

    @Operation(summary = "Batch get products by id", security = @SecurityRequirement(name = "security_auth"))
    @PostMapping(":batch-get")
    public List<ProductResponseDto> getProductsById(@RequestBody @Valid ItemRequestDto items) {
        return productService.getProductsById(items);
    }

    @Operation(summary = "Update product", security = @SecurityRequirement(name = "security_auth"))
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateProductRequestDto requestDto,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ProductResponseDto updated = productService.updateProduct(
                id, mapper.mapProductRequestToUpdateCommand(requestDto),
                IfMatchSupport.expectedVersion(ifMatch));
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag(updated.version()))
                .body(updated);
    }

    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /** Strong, opaque ETag derived from the optimistic-lock version. */
    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Cursor pages are ordered by {@code (createdAt, id)} only, so a whitelist is unnecessary:
     * every other sort — including a bare property or an unknown direction — is rejected.
     */
    private static String cursorDirection(String sort) {
        String[] parts = sort == null ? new String[0] : sort.trim().split(",", -1);
        if (parts.length == 2 && "createdAt".equals(parts[0].trim())) {
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if ("asc".equals(direction) || "desc".equals(direction)) {
                return direction;
            }
        }
        throw new ValidationException(
                "Unsupported sort: " + sort + "; use createdAt,asc or createdAt,desc");
    }
}
