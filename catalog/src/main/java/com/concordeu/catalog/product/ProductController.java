package com.concordeu.catalog.product;

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

    @PostMapping("/create-product/{categoryName}")
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductDto productDto, @PathVariable String categoryName) {
        validator.validateData(productDto, categoryName);

        ProductDto product = productService.createProduct(productDto, categoryName);
        log.info("The product has been created successfully: " + productDto.getName() + " in the category " + categoryName);

        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping("/get-products")
    public ResponseEntity<List<ProductDto>> getProducts(){
        return ResponseEntity.status(HttpStatus.OK).body(productService.getProducts());
    }

    @GetMapping("/get-products/{category}")
    public ResponseEntity<List<ProductDto>> getProductsByCategory(@PathVariable String category) {
        List<ProductDto> products = productService.getProductsByCategory(category);
        log.info("The products have been received");

        return ResponseEntity.status(HttpStatus.OK).body(products);
    }

    @PutMapping("/update-product/{productName}")
    public ResponseEntity<ProductDto> updateProduct(@RequestBody ProductDto productDto, @PathVariable String productName) {
        validator.validateData(productDto, productName);

        productService.updateProduct(productName, productDto);
        log.info("The product has been updated: " + productName);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/delete/{productName}")
    public ResponseEntity<ProductDto> deleteProduct(@PathVariable String productName) {
        productService.deleteProduct(productName);

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
