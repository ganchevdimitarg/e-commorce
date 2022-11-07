package com.concordeu.gateway.controller.catalog;

import com.concordeu.gateway.dto.catalog.product.ProductRequestDto;
import com.concordeu.gateway.dto.catalog.product.ProductResponseDto;
import com.concordeu.gateway.service.product.ProductService;
import com.concordeu.gateway.service.product.StringResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    @PostMapping("/create-product/{categoryName}")
    public Mono<ProductResponseDto> createProduct(@RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
                                                  @RequestBody ProductRequestDto requestDto,
                                                  @PathVariable String categoryName) {
        return productService.createProduct(authorizedClient, requestDto, categoryName);
    }

    @GetMapping(value = "/get-products")
    public Mono<ProductResponsePage<ProductResponseDto>> getArticles(
            @RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
            @RequestParam int page,
            @RequestParam int size) {
        Mono<ProductResponsePage<ProductResponseDto>> products = productService.getProductsByPage(authorizedClient, page, size);
        log.info("Success get products");
        return products;
    }

    @GetMapping("/get-products/{categoryName}")
    public Mono<ProductResponsePage<ProductResponseDto>> getProductsByCategory(@RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
                                                          @RequestParam int page,
                                                          @RequestParam int size,
                                                          @PathVariable String categoryName) {
        return productService.getProductsByCategoryByPage(authorizedClient, page, size, categoryName);
    }

    @GetMapping("/get-product/{productName}")
    public Mono<ProductResponseDto> getProductByName(@RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
                                               @PathVariable String productName) {
        return productService.getProductByName(authorizedClient, productName);
    }

    @PutMapping("/update-product/{productName}")
    public Mono<StringResponse> updateProduct(@RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
                                              @RequestBody ProductRequestDto requestDto,
                                              @PathVariable String productName) {
        return productService.updateProduct(authorizedClient, requestDto, productName);
    }

    @DeleteMapping("/delete-product/{productName}")
    public Mono<StringResponse> deleteProduct(@RegisteredOAuth2AuthorizedClient("authentication-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
                              @PathVariable String productName) {
        return productService.deleteProduct(authorizedClient, productName);
    }
}