package com.concordeu.order.service.impl;

import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Item;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.*;
import com.concordeu.order.excaption.InvalidRequestDataException;
import com.concordeu.order.repositories.OrderRepository;
import com.concordeu.order.service.ChargeService;
import com.concordeu.order.service.ItemService;
import com.concordeu.order.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    public static final String CIRCUIT_BREAKER_NAME = "orderService";

    private final OrderRepository orderRepository;
    private final ItemService itemService;
    private final ChargeService chargeService;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    private long orderCounter;

    @PostConstruct
    public void init() {
        orderCounter = 0L;
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
            throw new IllegalArgumentException(
                    String.format("User %s try to access another account %s", authenticationName, username)
            );
        }

        return webClient
                .get()
                .uri(profileServiceGetProfileByUsernameUri + authenticationName)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                                .run(it, throwable -> {
                                    log.warn("Profile Server is down", throwable);
                                    return Mono.just(UserDto.builder().username("").build());
                                })
                )
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
                                        .map(String::valueOf)
                                        .toList())
                                .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                                .run(it, throwable -> {
                                    log.warn("Catalog Server is down", throwable);
                                    return Mono.just(List.of(ProductResponseDto.builder().name("").build()));
                                })
                )
                .doOnError(throwable -> throwInvalidRequestDataException())
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
                .flatMap(payment -> saveAndReturnOrder(orderDto)
                        .flatMap(orderNew -> getItems(orderDto, orderNew)
                                .map(items -> Order.builder()
                                        .id(orderNew.getId())
                                        .orderNumber(orderNew.getOrderNumber())
                                        .username(orderNew.getUsername())
                                        .deliveryComment(orderNew.getDeliveryComment())
                                        .createdOn(orderNew.getCreatedOn())
                                        .updatedOn(orderNew.getUpdatedOn())
                                        .items(items)
                                        .charge(
                                                Charge.builder()
                                                        .chargeId(payment.chargeId())
                                                        .status(payment.chargeStatus())
                                                        .orderId(orderNew.getId())
                                                        .amount(payment.amount())
                                                        .currency(payment.currency())
                                                        .build()
                                        )
                                        .build())
                                .doOnSuccess(i -> log.info("Items was successfully created")))
                        .flatMap(order -> chargeService.saveCharge(order, payment)
                                .doOnSuccess(s -> log.info("Charge was successfully created"))
                                .map(charge -> OrderDto.builder()
                                        .id(order.getId())
                                        .orderNumber(order.getOrderNumber())
                                        .createdOn(order.getCreatedOn())
                                        .updatedOn(order.getUpdatedOn())
                                        .username(order.getUsername())
                                        .deliveryComment(order.getDeliveryComment())
                                        .items(order.getItems())
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
                                        .charge(ChargeDto.builder()
                                                .id(charge.getId())
                                                .chargeId(charge.getChargeId())
                                                .createOn(charge.getCreatedOn())
                                                .updateOn(charge.getUpdatedOn())
                                                .status(charge.getStatus())
                                                .orderId(charge.getOrderId())
                                                .amount(formatAmount(charge.getAmount()))
                                                .currency(charge.getCurrency())
                                                .build())
                                        .build()))
                        .doOnSuccess(o -> log.info("Order was successfully created")));
    }

    private Mono<List<Item>> getItems(OrderDto orderDto, Order order) {
        List<Item> items = orderDto.items();
        items.forEach(item -> item.setOrderId(order.getId()));
        return itemService.saveAll(items).collectList();
    }

    private Mono<Order> saveAndReturnOrder(OrderDto orderDto) {
        return orderRepository.save(
                Order.builder()
                        .username(orderDto.username())
                        .deliveryComment(orderDto.deliveryComment())
                        .orderNumber(++this.orderCounter)
                        .createdOn(LocalDateTime.now())
                        .build()
        );
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
                            reactiveCircuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
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

    @Transactional
    @Override
    public Mono<Void> deleteOrder(Long orderId) {
        return orderRepository.deleteOrderById(orderId)
                .doOnSuccess(s -> log.info("Order was successfully delete"));
    }

    @Override
    public Mono<OrderResponseDto> getOrder(Long orderId, String authenticationName) {
        return orderRepository.findOrderById(orderId)
                .doOnError(throwable -> {
                    log.warn("No such order");
                    throw new IllegalArgumentException("No such order");
                })
                .flatMap(order -> {
                    String username = order.getUsername();
                    if (!username.equals(authenticationName)) {
                        log.debug("User '{}' try to access another account '{}'", authenticationName, username);
                        return Mono.error(new IllegalArgumentException("You cannot access this information!"));
                    }
                    return sendRequestToProfileService(order, authenticationName);
                });
    }

    private Mono<OrderResponseDto> sendRequestToProfileService(Order order, String username) {
        return webClient
                .get()
                .uri(profileServiceGetProfileByUsernameUri + username)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                                .run(it, throwable -> {
                                    log.warn("Profile Server is down", throwable);
                                    return Mono.just(UserDto.builder().username("").build());
                                })
                )
                .doOnError(throwable -> {
                    throwInvalidRequestDataException();
                })
                .flatMap(userInfo -> sendRequestToCatalogService(order, userInfo));
    }

    private Mono<OrderResponseDto> sendRequestToCatalogService(Order order, UserDto userInfo) {
        return webClient
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
                                        .map(String::valueOf)
                                        .toList())
                                .build()
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                                .run(it, throwable -> {
                                    log.warn("Catalog Server is down", throwable);
                                    return Mono.just(List.of(ProductResponseDto.builder().name("").build()));
                                })
                )
                .doOnError(throwable -> throwInvalidRequestDataException())
                .map(productInfo ->
                        {
                            OrderResponseDto build = OrderResponseDto.builder()
                                    .userInfo(userInfo)
                                    .productInfo(productInfo)
                                    .orderNumber(order.getOrderNumber())
                                    .deliveryComment(order.getDeliveryComment())
                                    .createdOn(order.getCreatedOn())
                                    .build();
                            OrderDto b = OrderDto.builder()
                                    .id(order.getId())
                                    .orderNumber(order.getOrderNumber())
                                    .createdOn(order.getCreatedOn())
                                    .updatedOn(order.getUpdatedOn())
                                    .username(order.getUsername())
                                    .deliveryComment(order.getDeliveryComment())
                                    .items(order.getItems())
//                                    .firstName(orderDto.firstName())
//                                    .lastName(orderDto.lastName())
//                                    .phoneNumber(orderDto.phoneNumber())
//                                    .city(orderDto.city())
//                                    .street(orderDto.street())
//                                    .postCode(orderDto.postCode())
//                                    .cardNumber(orderDto.cardNumber())
//                                    .cardExpMonth(orderDto.cardExpMonth())
//                                    .cardExpYear(orderDto.cardExpYear())
//                                    .cardCvc(orderDto.cardCvc())
//                                    .charge(ChargeDto.builder()
//                                            .id(charge.getId())
//                                            .chargeId(charge.getChargeId())
//                                            .createOn(charge.getCreatedOn())
//                                            .updateOn(charge.getUpdatedOn())
//                                            .status(charge.getStatus())
//                                            .orderId(charge.getOrderId())
//                                            .amount(formatAmount(charge.getAmount()))
//                                            .currency(charge.getCurrency())
//                                            .build())
                                    .build();
                            return build;
                        }
                );
    }

    private static void throwInvalidRequestDataException() {
        throw new InvalidRequestDataException("""
                Something happened with the order service.
                Please check the request details again
                """);
    }

    public static String formatAmount(long value) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        return decimalFormat.format(value / 100.0);
    }
}
