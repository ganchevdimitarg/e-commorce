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
import java.util.UUID;

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
    public ResponseEntity<Void> updateProduct(@PathVariable UUID id,
                                              @RequestBody @Valid ProductRequestDto requestDto) {
        productService.updateProduct(id, mapper.mapProductRequestToUpdateCommand(requestDto));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "security_auth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
