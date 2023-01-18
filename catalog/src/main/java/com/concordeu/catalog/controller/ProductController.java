package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.product.ItemRequestDto;
import com.concordeu.catalog.dto.product.ProductRequestDto;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final MapStructMapper mapper;

    @Operation(summary = "Create Product", description = "Create a product in the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PostMapping("/create-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public ProductResponseDto createProduct(@RequestBody ProductRequestDto requestDto,
                                            @RequestParam String categoryName) {
        ProductResponseDto productResponseDto = mapper.mapProductRequestDtoToProductResponseDto(requestDto);
        return productService.createProduct(productResponseDto, categoryName);
    }

    @Operation(summary = "Get Products", description = "Get all products from the database",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-products")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProducts(@RequestParam int page,
                                                @RequestParam int size) {
        return productService.getProductsByPage(page, size);
    }

    @Operation(summary = "Get Products By Category Name", description = "Get all products by category name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-category-products")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public Page<ProductResponseDto> getProductsByCategory(@RequestParam int page,
                                                          @RequestParam int size,
                                                          @RequestParam String categoryName) {
        return productService.getProductsByCategoryByPage(page, size, categoryName);
    }

    @Operation(summary = "Get Product Product Name", description = "Get product by product name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping("/get-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductResponseDto getProductByName(@RequestParam String productName) {
        return productService.getProductByName(productName);
    }

    @Operation(summary = "Get Product Id", description = "Get product by product id",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })

    @GetMapping("/get-product-id")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductResponseDto getProductById(@RequestParam String productId) {
        return productService.getProductById(productId);
    }

    @PostMapping("/get-products-id")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public List<ProductResponseDto> getProductsById(@RequestBody ItemRequestDto items) {
        return productService.getProductsById(items);
    }

    @Operation(summary = "Update Product By Product Name", description = "Update product by product name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @PutMapping("/update-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void updateProduct(@RequestBody ProductRequestDto requestDto,
                              @RequestParam String productName) {
        ProductResponseDto productResponseDto = mapper.mapProductRequestDtoToProductResponseDto(requestDto);
        productService.updateProduct(productResponseDto, productName);
    }

    @Operation(summary = "Delete Product By Product Name", description = "Delete product by product name",
            security = @SecurityRequirement(name = "security_auth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @DeleteMapping("/delete-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void deleteProduct(@RequestParam String productName) {
        productService.deleteProduct(productName);
    }


}
