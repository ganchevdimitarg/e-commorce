package com.concordeu.auth.controller;

import com.concordeu.auth.service.catalog.ProductService;
import com.concordeu.client.catalog.product.ProductRequestDto;
import com.concordeu.client.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    private final ProductService productService;

    @PostMapping("/create-product/{categoryName}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ProductResponseDto createProduct(@RequestBody ProductRequestDto requestDto,
                                            @PathVariable String categoryName) {
        return productService.createProduct(requestDto, categoryName);
    }

    @GetMapping("/get-products")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<ProductResponseDto> getProducts(@RequestParam int page,
                                                @RequestParam int pageSize) {
        return productService.getProductsByPage(page, pageSize);
    }

    @GetMapping("/get-products/{categoryName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public Page<ProductResponseDto> getProductsByCategory(@RequestParam int page,
                                                  @RequestParam int pageSize,
                                                  @PathVariable String categoryName) {
        return productService.getProductsByCategoryByPage(page, pageSize, categoryName);
    }

    @GetMapping("/get-product/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public ProductResponseDto getProductByName(@PathVariable String productName){
        return productService.getProductsByName(productName);
    }

    @PutMapping("/update-product/{productName}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_WORKER')")
    public void updateProduct(@RequestBody ProductRequestDto requestDto,
                              @PathVariable String productName) {
        productService.updateProduct(requestDto, productName);
    }

    @DeleteMapping("/delete/{productName}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);
    }
}
