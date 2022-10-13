package com.concordeu.catalog.controller;

import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dto.ProductRequestDto;
import com.concordeu.catalog.validator.ProductDataValidator;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ProductDataValidator validator;
    private final MapStructMapper mapper;

    @PostMapping("/create-product/{categoryName}")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String categoryName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        validator.validateData(productDto, categoryName);

        ProductDto product = productService.createProduct(productDto, categoryName);
        log.info("The product has been created successfully: " + productDto.getName() + " in the category " + categoryName);

        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping("/get-products")
    public ResponseEntity<List<ProductDto>> getProducts(){
        return ResponseEntity.status(HttpStatus.OK).body(productService.getProducts());
    }

    @GetMapping("/get-products/{categoryName}")
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable String categoryName) {
        List<ProductDto> products = productService.getProductsByCategory(categoryName);
        log.info("The products have been received");

        return ResponseEntity.status(HttpStatus.OK).body(products);
    }

    @PutMapping("/update-product/{productName}")
    public ResponseEntity<ProductDto> updateProduct(@RequestBody ProductRequestDto requestDto, @PathVariable String productName) {
        ProductDto productDto = mapper.mapProductRequestDtoToProductDto(requestDto);
        validator.validateData(productDto, productName);

        productService.updateProduct(productDto, productName);
        log.info("The product has been updated: " + productName);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/delete/{productName}")
    public ResponseEntity<ProductDto> deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}