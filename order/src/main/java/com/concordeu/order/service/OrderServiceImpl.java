package com.concordeu.order.service;

import com.concordeu.order.dao.ItemDao;
import com.concordeu.order.dao.OrderDao;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderDao orderDao;
    private final ItemDao itemDao;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ChargeService chargeService;

    @Value("${catalog.service.products.get.uri}")
    private String catalogProductsGetUri;
    @Value("${catalog.service.profile.get.uri}")
    private String catalogProfileGetUri;

    private long orderCounter = 1;

    @Override
    public void createOrder(OrderDto orderDto, String authenticationName) {
        String username = orderDto.username();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        List<ProductResponseDto> products = getRequestToCategoryService(
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

        PaymentDto payment = chargeService.makePayment(orderDto, authenticationName, amount);

        if (payment.chargeStatus().equals("succeeded")) {
            Order order = Order.builder()
                    .username(orderDto.username())
                    .deliveryComment(orderDto.deliveryComment())
                    .orderNumber(orderCounter++)
                    .createdOn(LocalDateTime.now())
                    .build();
            Order orderSave = orderDao.saveAndFlush(order);
            log.info("Order was successfully created");

            List<Item> items = orderDto.items();
            items.forEach(item -> item.setOrder(orderSave));
            itemDao.saveAllAndFlush(items);
            log.info("Items was successfully created");

            chargeService.saveCharge(order, payment);
        }
    }

    @Override
    public void deleteOrder(long orderNumber) {
        orderDao.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");
    }

    @Override
    public OrderResponseDto getOrder(long orderNumber, String authenticationName) {
        Optional<Order> order = orderDao.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String username = order.get().getUsername();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = webClient
                .get()
                .uri(catalogProfileGetUri + username)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> (Mono.just(UserDto.builder().username(username).build())))
                )
                .block();


        List<ProductResponseDto> productInfo = getRequestToCategoryService(
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

    private List<ProductResponseDto> getRequestToCategoryService(ItemRequestDto request) {
        return webClient
                .post()
                .uri(catalogProductsGetUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it)
                )
                .block();
    }
}
