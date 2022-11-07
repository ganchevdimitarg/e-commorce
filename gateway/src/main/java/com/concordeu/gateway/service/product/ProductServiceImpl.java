package com.concordeu.gateway.service.product;

import com.concordeu.gateway.controller.catalog.ProductResponsePage;
import com.concordeu.gateway.dto.catalog.category.CategoryResponseDto;
import com.concordeu.gateway.dto.catalog.product.ProductRequestDto;
import com.concordeu.gateway.dto.catalog.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

@Service
//@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final WebClient webClient;
    private final ReactiveCircuitBreaker reactiveCircuitBreaker;

    @Value("${catalog.product.url}")
    private String catalogProductUrl;

    public ProductServiceImpl(WebClient webClient, ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory) {
        this.webClient = webClient;
        this.reactiveCircuitBreaker = reactiveCircuitBreakerFactory.create("catalog-service");
    }

    @Override
    public Mono<ProductResponseDto> createProduct(OAuth2AuthorizedClient authorizedClient, ProductRequestDto productResponseDto, String categoryName) {
        return this.webClient
                .post()
                .uri(catalogProductUrl + "/create-product/{categoryName}", categoryName)
                .body(productResponseDto, ProductRequestDto.class)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponseDto>() {
                })
                .transform(response -> reactiveCircuitBreaker
                        .run(response, throwable -> Mono.just(new ProductResponseDto(
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
                );
    }

    @Override
    public Mono<ProductResponsePage<ProductResponseDto>> getProductsByPage(OAuth2AuthorizedClient authorizedClient, int page, int size) {
        return this.webClient
                .get()
                .uri(catalogProductUrl + "/get-products?page={page}&size={size}", page, size)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponsePage<ProductResponseDto>>() {
                })
                .transform(response -> reactiveCircuitBreaker
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

    @Override
    public Mono<ProductResponsePage<ProductResponseDto>> getProductsByCategoryByPage(OAuth2AuthorizedClient authorizedClient, int page, int size, String categoryName) {
        return this.webClient
                .get()
                .uri(catalogProductUrl + "/get-products/{categoryName}?page={page}&size={size}", categoryName, page, size)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponsePage<ProductResponseDto>>() {
                })
                .transform(response -> reactiveCircuitBreaker
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

    @Override
    public Mono<ProductResponseDto> getProductByName(OAuth2AuthorizedClient authorizedClient, String productName) {
        return this.webClient
                .get()
                .uri(catalogProductUrl + "/get-product/{productName}", productName)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ProductResponseDto>() {
                })
                .transform(response -> reactiveCircuitBreaker
                        .run(response, throwable -> Mono.just(new ProductResponseDto(
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
                );
    }

   @Override
    public Mono<StringResponse> updateProduct(OAuth2AuthorizedClient authorizedClient, ProductRequestDto productRequestDto, String productName) {
        return this.webClient
                .put()
                .uri(catalogProductUrl + "/update-product/{productName}", productName)
                .body(productRequestDto, ProductRequestDto.class)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(StringResponse.class)
                .defaultIfEmpty(new StringResponse("The product was successfully updated"))
                .transform(response -> reactiveCircuitBreaker
                        .run(response, throwable -> Mono.just(new StringResponse("Sorry! Something went wrong...")))
                );
    }

    @Override
    public Mono<StringResponse> deleteProduct(OAuth2AuthorizedClient authorizedClient, String productName) {
        return this.webClient
                .get()
                .uri(catalogProductUrl + "/delete/{productName}", productName)
                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(StringResponse.class)
                .defaultIfEmpty(new StringResponse("The product was successfully deleted"))
                .transform(response -> reactiveCircuitBreaker
                        .run(response, throwable -> Mono.just(new StringResponse("Sorry! Something went wrong...")))
                );
    }
}
