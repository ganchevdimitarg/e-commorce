package com.concordeu.order.service;

import com.concordeu.order.domain.Charge;
import com.concordeu.order.excaption.InvalidRequestDataException;
import com.concordeu.order.repositories.ItemRepository;
import com.concordeu.order.repositories.OrderRepository;
import com.concordeu.order.domain.Item;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ChargeService chargeService;

    private long orderCounter;

    @PostConstruct
    public void init() {
        orderCounter = orderRepository.count();
    }

    @Value("${catalog.service.products.get.uri}")
    private String catalogServiceGetProductsByIdsUri;
    @Value("${profile.service.get.uri}")
    private String profileServiceGetProfileByUsernameUri;
    @Value("${profile.service.post.uri}")
    private String profileServiceCreateUserUri;

    @Transactional
    @Override
    public Mono<OrderDto> createOrder(OrderDto orderDto, String authenticationName) {
        String username = orderDto.username();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        return webClient
                .get()
                .uri(profileServiceGetProfileByUsernameUri + authenticationName)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Profile Server is down", throwable);
                                    return Mono.just(UserDto.builder().username("").build());
                                })
                )
                .publishOn(Schedulers.boundedElastic())
                .flatMap(user -> getUserDto(orderDto, user))
                .flatMap(userInfo -> saveCharge(orderDto, authenticationName, userInfo));
    }

    private Mono<OrderDto> saveCharge(OrderDto orderDto, String authenticationName, UserDto userInfo) {
        return webClient
                .post()
                .uri(catalogServiceGetProductsByIdsUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(
                        ItemRequestDto.builder()
                                .items(orderDto.items()
                                        .stream()
                                        .map(Item::getProductId)
                                        .toList())
                                .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Catalog Server is down", throwable);
                                    return Mono.just(List.of(ProductResponseDto.builder().name("").build()));
                                })
                )
                .doOnError(throwable -> {
                    throw new InvalidRequestDataException("""
                            Something happened with the order service.
                            Please check the request details again
                            """);
                })
                .flatMap(products -> {
                    long amount = Long.parseLong(
                            products.stream()
                                    .map(ProductResponseDto::price)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .toString()
                                    .replace(".", "")
                    );
                    return chargeService.makePayment(Objects.requireNonNull(userInfo).cardId(), authenticationName, amount);
                })
                .publishOn(Schedulers.boundedElastic())
                .map(charge -> {
                    Order order = Order.builder()
                            .username(orderDto.username())
                            .deliveryComment(orderDto.deliveryComment())
                            .orderNumber(++orderCounter)
                            .createdOn(OffsetDateTime.now())
                            .build();

                    Order orderSave = orderRepository.saveAndFlush(order);
                    log.info("Order was successfully created");

                    List<Item> items = orderDto.items();
                    items.forEach(item -> item.setOrder(orderSave));
                    List<Item> itemsSave = itemRepository.saveAllAndFlush(items);
                    log.info("Items was successfully created");

                    chargeService.saveCharge(order, charge).subscribe();

                    return OrderDto.builder()
                            .orderNumber(orderSave.getOrderNumber())
                            .username(orderSave.getUsername())
                            .deliveryComment(orderSave.getDeliveryComment())
                            .items(itemsSave)
                            .build();
                });
    }

    private Mono<UserDto> getUserDto(OrderDto orderDto, UserDto user) {
        Mono<UserDto> userInfo;
        if (user.username().isEmpty()) {
            UserDto profileRequest = UserDto.builder()
                    .username(orderDto.username())
                    .password("opaque")
                    .firstName(orderDto.firstName())
                    .lastName(orderDto.lastName())
                    .phoneNumber(orderDto.phoneNumber())
                    .city(orderDto.city())
                    .street(orderDto.street())
                    .postCode(orderDto.postCode())
                    .cardNumber(orderDto.cardNumber())
                    .cardExpMonth(orderDto.cardExpMonth())
                    .cardExpYear(orderDto.cardExpYear())
                    .cardCvc(orderDto.cardCvc())
                    .build();

            userInfo = webClient
                    .post()
                    .uri(profileServiceCreateUserUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(profileRequest)
                    .retrieve()
                    .bodyToMono(UserDto.class)
                    .transform(it ->
                            reactiveCircuitBreakerFactory.create("orderService")
                                    .run(it, throwable -> {
                                        log.warn("Profile Server is down", throwable);
                                        return Mono.just(UserDto.builder().username("").build());
                                    })
                    );
        } else {
            userInfo = Mono.just(user);
        }
        return userInfo;
    }

    @Override
    public void deleteOrder(long orderNumber) {
        orderRepository.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");
    }

    @Override
    public Mono<OrderResponseDto> getOrder(long orderNumber, String authenticationName) {
        Order order = orderRepository.findByOrderNumber(orderNumber);
        if (order == null) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String username = order.getUsername();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        return webClient
                .get()
                .uri(profileServiceGetProfileByUsernameUri + username)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Profile Server is down", throwable);
                                    return Mono.just(UserDto.builder().username("").build());
                                })
                )
                .doOnError(throwable -> {
                    throw new InvalidRequestDataException("""
                            Something happened with the order service.
                            Please check the request details again
                            """);
                })
                .flatMap(userInfo -> webClient
                        .post()
                        .uri(catalogServiceGetProductsByIdsUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                ItemRequestDto.builder()
                                        .items(order
                                                .getItems()
                                                .stream()
                                                .map(Item::getProductId)
                                                .toList())
                                        .build()
                        )
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                        })
                        .transform(it ->
                                reactiveCircuitBreakerFactory.create("orderService")
                                        .run(it, throwable -> {
                                            log.warn("Catalog Server is down", throwable);
                                            return Mono.just(List.of(ProductResponseDto.builder().name("").build()));
                                        })
                        )
                        .doOnError(throwable -> {
                            throw new InvalidRequestDataException("""
                                    Something happened with the order service.
                                    Please check the request details again
                                    """);
                        })
                        .map(productInfo ->
                                OrderResponseDto.builder()
                                        .userInfo(userInfo)
                                        .productInfo(productInfo)
                                        .orderNumber(order.getOrderNumber())
                                        .deliveryComment(order.getDeliveryComment())
                                        .createdOn(order.getCreatedOn())
                                        .build()
                        ));
    }
}
