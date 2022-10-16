package com.concordeu.catalog.controller;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.ProductRequestDto;
import com.concordeu.catalog.validator.ProductDataValidator;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final MapStructMapper mapper;

    @PostMapping("/create-product/{categoryName}")
    public ProductDto createProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String categoryName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        return productService.createProduct(productDto, categoryName);
    }

    @GetMapping("/get-products")
    public Page<ProductDto> getProducts(@RequestParam int page, @RequestParam int pageSize) {
        return productService.getProductsByPage(page, pageSize);
    }

    @GetMapping("/get-products/{categoryName}")
    public Page<ProductDto> getProductsByCategory(@RequestParam int page, @RequestParam int pageSize, @PathVariable String categoryName) {
        return productService.getProductsByCategoryByPage(page, pageSize, categoryName);
    }

    @PutMapping("/update-product/{productName}")
    public void updateProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String productName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        productService.updateProduct(productDto, productName);
    }

    @DeleteMapping("/delete/{productName}")
    public void deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);
    }
}
