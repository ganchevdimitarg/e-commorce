package com.concordeu.catalog.controller;

import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.client.catalog.product.ProductRequestDto;
import com.concordeu.client.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final MapStructMapper mapper;

    @PostMapping("/create-product/{categoryName}")
    public ProductResponseDto createProduct(@RequestBody ProductRequestDto requestDto,
                                            @PathVariable String categoryName) {
        ProductResponseDto productResponseDto = mapper.mapProductRequestDtoToProductResponseDto(requestDto);
        return productService.createProduct(productResponseDto, categoryName);
    }

    @GetMapping("/get-products")
    public Page<ProductResponseDto> getProducts(@RequestParam int page,
                                                @RequestParam int pageSize) {
        return productService.getProductsByPage(page, pageSize);
    }

    @GetMapping("/get-products/{categoryName}")
    public Page<ProductResponseDto> getProductsByCategory(@RequestParam int page,
                                                          @RequestParam int pageSize,
                                                          @PathVariable String categoryName) {
        return productService.getProductsByCategoryByPage(page, pageSize, categoryName);
    }

    @GetMapping("/get-product/{productName}")
    public ProductResponseDto getProductByName(@PathVariable String productName){
        return productService.getProductByName(productName);
    }

    @PutMapping("/update-product/{productName}")
    public void updateProduct(@RequestBody ProductRequestDto requestDto,
                              @PathVariable String productName) {
        ProductResponseDto productResponseDto = mapper.mapProductRequestDtoToProductResponseDto(requestDto);
        productService.updateProduct(productResponseDto, productName);
    }

    @DeleteMapping("/delete/{productName}")
    public void deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);
    }
}
