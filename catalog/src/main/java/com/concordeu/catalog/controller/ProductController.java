package com.concordeu.catalog.controller;

import com.concordeu.catalog.dto.ProductDTO;
import com.concordeu.catalog.mapper.ProductMapper;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.client.common.dto.ItemRequestDto;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/product")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ProductMapper mapper;


    @PostMapping("/create-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @Observed(
            name = "user.name",
            contextualName = "createProduct",
            lowCardinalityKeyValues = {"method", "createProduct"}
    )
    public ProductDTO createProduct(@RequestBody ProductDTO requestDto,
                                    @RequestParam String categoryName) {
        return productService.createProduct(requestDto, categoryName);
    }

    @GetMapping("/get-products")
    @Observed(
            name = "user.name",
            contextualName = "getProducts",
            lowCardinalityKeyValues = {"method", "getProducts"}
    )
    public Page<ProductDTO> getProducts(@RequestParam int page,
                                        @RequestParam int size) {
        return productService.getProductsByPage(page, size);
    }

    @GetMapping("/get-category-products")
    @Observed(
            name = "user.name",
            contextualName = "getProductsByCategory",
            lowCardinalityKeyValues = {"method", "getProductsByCategory"}
    )
    public Page<ProductDTO> getProductsByCategory(@RequestParam int page,
                                                  @RequestParam int size,
                                                  @RequestParam String categoryName) {
        return productService.getProductsByCategoryByPage(page, size, categoryName);
    }

    @GetMapping("/get-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Observed(
            name = "user.name",
            contextualName = "getProductByName",
            lowCardinalityKeyValues = {"method", "getProductByName"}
    )
    public ProductDTO getProductByName(@RequestParam String productName) {
        return productService.getProductByName(productName);
    }

    @GetMapping("/get-product-id")
    @PreAuthorize("hasAuthority('SCOPE_catalog.read')")
    @Observed(
            name = "user.name",
            contextualName = "getProductById",
            lowCardinalityKeyValues = {"method", "getProductById"}
    )
    public ProductDTO getProductById(@RequestParam UUID productId) {
        return productService.getProductById(productId);
    }

    @PostMapping("/get-products-id")
    @Observed(
            name = "user.name",
            contextualName = "getProductsById",
            lowCardinalityKeyValues = {"method", "getProductsById"}
    )
    public List<ProductDTO> getProductsById(@RequestBody ItemRequestDto items) {
        return productService.getProductsById(items.items());
    }

    @PutMapping("/update-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @Observed(
            name = "user.name",
            contextualName = "updateProduct",
            lowCardinalityKeyValues = {"method", "updateProduct"}
    )
    public void updateProduct(@RequestBody ProductDTO requestDto,
                              @RequestParam String productName) {

        productService.updateProduct(requestDto, productName);
    }

    @DeleteMapping("/delete-product")
    @PreAuthorize("hasAuthority('SCOPE_catalog.write')")
    @Observed(
            name = "user.name",
            contextualName = "deleteProduct",
            lowCardinalityKeyValues = {"method", "deleteProduct"}
    )
    public void deleteProduct(@RequestParam String productName) {
        productService.deleteProduct(productName);
    }


}
