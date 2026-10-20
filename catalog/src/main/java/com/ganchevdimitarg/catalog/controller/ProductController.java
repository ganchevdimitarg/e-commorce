package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.dto.PageResponse;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductRequestDto;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
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
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + created.id())).body(created);
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

    @Operation(summary = "Get product by id", security = @SecurityRequirement(name = "security_auth"))
    @GetMapping("/{id}")
    public ProductResponseDto getProductById(@PathVariable UUID id) {
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
    public ResponseEntity<ProductResponseDto> updateProduct(@PathVariable UUID id,
                                                            @RequestBody @Valid UpdateProductRequestDto requestDto) {
        return ResponseEntity.ok(productService.updateProduct(
                id, mapper.mapProductRequestToUpdateCommand(requestDto)));
    }

    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
