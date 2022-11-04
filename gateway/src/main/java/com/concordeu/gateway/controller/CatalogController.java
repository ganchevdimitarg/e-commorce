package com.concordeu.gateway.controller;

import com.concordeu.client.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CatalogController {
    private final WebClient webClient;

    @GetMapping(value = "/api/v1/catalog/products/{page}/{size}")
    public Page<ProductResponseDto> getArticles(
            @RegisteredOAuth2AuthorizedClient("catalog-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
            @PathVariable int page,
            @PathVariable int size) {
        return this.webClient
                .get()
                .uri("http://localhost:8083/api/v1/catalog/product/get-products?page={page}&pageSize={pageSize}", page, size)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Page<ProductResponseDto>>() {
                })
                .block();
    }
}
