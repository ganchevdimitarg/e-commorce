package com.concordeu.order.service;

import com.concordeu.order.excaption.InvalidRequestDataException;
import com.concordeu.order.repositories.ItemRepository;
import com.concordeu.order.repositories.OrderRepository;
import com.concordeu.order.domain.Item;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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

    @Override
    public void createOrder(OrderDto orderDto, String authenticationName) {
        String username = orderDto.username();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);
        if (userInfo.username().isEmpty()) {
            userInfo = createProfileUser(orderDto);
        }

        List<ProductResponseDto> products = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(orderDto.items()
                                .stream()
                                .map(Item::getProductId)
                                .toList())
                        .build()
        );

        long amount = Long.parseLong(
                products.stream()
                        .map(ProductResponseDto::price)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .toString()
                        .replace(".", "")
        );

        PaymentDto payment = chargeService.makePayment(userInfo.cardId(), authenticationName, amount);

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
        itemRepository.saveAllAndFlush(items);
        log.info("Items was successfully created");

        chargeService.saveCharge(order, payment);
    }

    @Override
    public void deleteOrder(long orderNumber) {
        orderRepository.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");
    }

    @Override
    public OrderResponseDto getOrder(long orderNumber, String authenticationName) {
        Optional<Order> order = orderRepository.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String username = order.get().getUsername();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = getRequestToProfileServiceUserInfo(authenticationName);

        checkAvailabilityOfCatalogService(userInfo.username());

        List<ProductResponseDto> productInfo = getRequestToCategoryServiceProductInfo(
                ItemRequestDto.builder()
                        .items(order.get()
                                .getItems()
                                .stream()
                                .map(Item::getProductId)
                                .toList())
                        .build()
        );

        return OrderResponseDto.builder()
                .userInfo(userInfo)
                .productInfo(productInfo)
                .orderNumber(order.get().getOrderNumber())
                .deliveryComment(order.get().getDeliveryComment())
                .createdOn(order.get().getCreatedOn())
                .build();
    }

    private List<ProductResponseDto> getRequestToCategoryServiceProductInfo(ItemRequestDto request) {
        List<ProductResponseDto> responseDtoList = webClient
                .post()
                .uri(catalogServiceGetProductsByIdsUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
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
                .block();

        checkAvailabilityOfCatalogService(responseDtoList.get(0).name());
        return responseDtoList;
    }

    private void checkAvailabilityOfCatalogService(String token) {
        if (token.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the order service.
                    Please check the request details again
                    """);
        }
    }

    private UserDto getRequestToProfileServiceUserInfo(String username) {
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
                .block();

    }

    private UserDto createProfileUser(OrderDto orderDto) {
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

        return webClient
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
                )
                .block();
    }
}
