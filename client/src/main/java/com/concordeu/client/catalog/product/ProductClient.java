package com.concordeu.client.catalog.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "product", url = "http://localhost:8081/api/v1/catalog/product")
public interface ProductClient {

    @PostMapping("/create-product/{categoryName}")
    ProductResponseDto createProduct(@RequestBody ProductRequestDto requestDto,
                                     @PathVariable String categoryName);

    @GetMapping("/get-products")
    Page<ProductResponseDto> getProducts(@RequestParam int page,
                                         @RequestParam int pageSize);

    @GetMapping("/get-products/{categoryName}")
    Page<ProductResponseDto> getProductsByCategory(@RequestParam int page,
                                                   @RequestParam int pageSize,
                                                   @PathVariable String categoryName);

    @GetMapping("/get-product/{productName}")
    ProductResponseDto getProductByName(@PathVariable String productName);

    @PutMapping("/update-product/{productName}")
    void updateProduct(@RequestBody ProductRequestDto requestDto,
                       @PathVariable String productName);

    @DeleteMapping("/delete/{productName}")
    void deleteProduct(@PathVariable String productName);
}
