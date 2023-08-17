package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.ItemRequestDTO;
import com.concordeu.catalog.dto.ProductDTO;
import com.concordeu.catalog.mapper.ProductMapper;
import com.concordeu.catalog.service.product.ProductService;
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
    private final ProductMapper mapper;


    @PostMapping("/create-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public ProductDTO createProduct(@RequestBody ProductDTO requestDto,
                                    @RequestParam String categoryName) {
        return productService.createProduct(requestDto, categoryName);
    }

    @GetMapping("/get-products")
    public Page<ProductDTO> getProducts(@RequestParam int page,
                                        @RequestParam int size) {
        return productService.getProductsByPage(page, size);
    }

    @GetMapping("/get-category-products")
    public Page<ProductDTO> getProductsByCategory(@RequestParam int page,
                                                  @RequestParam int size,
                                                  @RequestParam String categoryName) {
        return productService.getProductsByCategoryByPage(page, size, categoryName);
    }

    @GetMapping("/get-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductDTO getProductByName(@RequestParam String productName) {
        return productService.getProductByName(productName);
    }

    @GetMapping("/get-product-id")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    public ProductDTO getProductById(@RequestParam String productId) {
        return productService.getProductById(productId);
    }

    @PostMapping("/get-products-id")
    public List<ProductDTO> getProductsById(@RequestBody ItemRequestDTO items) {
        return productService.getProductsById(items);
    }

    @PutMapping("/update-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void updateProduct(@RequestBody ProductDTO requestDto,
                              @RequestParam String productName) {

        productService.updateProduct(requestDto, productName);
    }

    @DeleteMapping("/delete-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    public void deleteProduct(@RequestParam String productName) {
        productService.deleteProduct(productName);
    }


}
