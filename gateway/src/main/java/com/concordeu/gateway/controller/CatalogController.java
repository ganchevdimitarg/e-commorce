package com.concordeu.gateway.controller;

import com.concordeu.gateway.dto.catalog.category.CategoryResponseDto;
import com.concordeu.gateway.dto.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CatalogController {
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    @GetMapping(value = "/api/v1/catalog/products/get-products")
    public Mono<ProductResponsePage<ProductResponseDto>> getArticles(
            @RegisteredOAuth2AuthorizedClient("catalog-client-authorization-code") OAuth2AuthorizedClient authorizedClient,
            @RequestParam int page,
            @RequestParam int size) {
        return this.webClient
                .get()
                .uri("http://localhost:8083/api/v1/catalog/products/get-products?page={page}&size={size}", page, size)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponsePage<ProductResponseDto>>() {
                })
                .transform(response -> reactiveCircuitBreakerFactory
                        .create("catalog-service")
                        .run(response, throwable -> Mono.just(new ProductResponsePage<>(
                                                List.of(new ProductResponseDto(
                                                                "2",
                                                                "error",
                                                                "error and error",
                                                                BigDecimal.valueOf(33.33),
                                                                true,
                                                                "",
                                                                new CategoryResponseDto("2", "error", null),
                                                                new ArrayList<>()
                                                        )
                                                )
                                        )
                                )
                        )
                );
    }
}

/*return this.webClient
                .get()
                .uri("http://localhost:8083/api/v1/catalog/products/get-products?page={page}&size={size}", page, size)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponsePage<ProductResponseDto>>() {
                })
                .block();*/
