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
import org.springframework.security.core.Authentication;
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
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private final OrderDao orderDao;
    private final ItemDao itemDao;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ChargeService chargeService;
    @Value("${gateway.uri}")
    private String gatewayUri;

    @Override
    public void createOrder(OrderDto orderDto, Authentication authentication, String authorization) {
        String username = orderDto.username();
        String authenticationName = authentication.getName();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        List<ProductResponseDto> products = getRequestToCategoryService(authorization,
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

        PaymentDto payment = chargeService.makePayment(orderDto, authorization, authenticationName, amount);

        if (payment.chargeStatus().equals("succeeded")) {
            Order order = Order.builder()
                    .username(orderDto.username())
                    .deliveryComment(orderDto.deliveryComment())
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

    //TODO refactor it. Does not work
    @Override
    public OrderResponseDto getOrder(long orderNumber, String authorization, Authentication authentication) {
        Optional<Order> order = orderDao.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String username = order.get().getUsername();
        String authenticationName = authentication.getName();
        if (!username.equals(authenticationName)) {
            log.debug("User '{}' try to access another account '{}'", authenticationName, username);
            throw new IllegalArgumentException("You cannot access this information!");
        }

        UserDto userInfo = webClient
                .get()
                .uri(gatewayUri + "/profile/get-by-username?username={username}", username)
                .header(HEADER_AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("order-service")
                                .run(it, throwable -> (Mono.just(UserDto.builder().username(username).build())))
                )
                .block();

        List<Item> productId = order.get().getItems();
        ProductResponseDto productInfo = webClient
                .get()
                .uri(gatewayUri + "/catalog/product/get-product-id?productId={productId}", productId)
                .header(HEADER_AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("order-service")
                                .run(it, throwable -> (Mono.just(ProductResponseDto.builder().build())))
                )
                .block();

        //TODO refactor it. Does not work
        return new OrderResponseDto(
                userInfo,
                productInfo,
                order.get().getOrderNumber(),
                order.get().getDeliveryComment(),
                order.get().getCreatedOn()
        );
    }

    private List<ProductResponseDto> getRequestToCategoryService(String authorizationToken, ItemRequestDto request) {
        return webClient
                .post()
                .uri(gatewayUri + "/catalog/product/get-products-id")
                .header(HEADER_AUTHORIZATION, authorizationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponseDto>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("order-service")
                                .run(it)
                )
                .block();
    }
}
