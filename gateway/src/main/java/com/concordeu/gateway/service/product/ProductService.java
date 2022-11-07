package com.concordeu.gateway.service.product;

import com.concordeu.gateway.controller.catalog.ProductResponsePage;
import com.concordeu.gateway.dto.catalog.product.ProductRequestDto;
import com.concordeu.gateway.dto.catalog.product.ProductResponseDto;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import reactor.core.publisher.Mono;

public interface ProductService {
    Mono<ProductResponseDto> createProduct(OAuth2AuthorizedClient authorizedClient, ProductRequestDto productResponseDto, String categoryName);

    Mono<ProductResponsePage<ProductResponseDto>> getProductsByPage(OAuth2AuthorizedClient authorizedClient, int page, int size);

    Mono<ProductResponsePage<ProductResponseDto>> getProductsByCategoryByPage(OAuth2AuthorizedClient authorizedClient, int page, int size, String categoryName);

    Mono<ProductResponseDto> getProductByName(OAuth2AuthorizedClient authorizedClient, String name);

    Mono<StringResponse> updateProduct(OAuth2AuthorizedClient authorizedClient, ProductRequestDto productRequestDto, String productName);

    Mono<StringResponse> deleteProduct(OAuth2AuthorizedClient authorizedClient, String productName);
}
