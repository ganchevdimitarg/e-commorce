package com.concordeu.order.service;

import com.concordeu.order.dao.OrderDao;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.dto.ProductResponseDto;
import com.concordeu.order.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private static final String BASE_URI = "http://127.0.0.1:8081/api/v1";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private final OrderDao orderDao;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    @Override
    public void createOrder(OrderDto orderDto) {
        Order entity = mapOrderDtoToOrder(orderDto);
        entity.setCreatedOn(LocalDateTime.now());
        entity.setOrderNumber(orderDao.count() + 1);
        orderDao.saveAndFlush(entity);
        log.info("Order was successfully created");
    }

    @Override
    public void deleteOrder(long orderNumber) {
        orderDao.deleteByOrderNumber(orderNumber);
        log.info("Order was successfully delete");
    }

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
                .uri(BASE_URI + "/profile/get-by-username?username={username}", username)
                .header(HEADER_AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UserDto.class)
                .transform(it ->
                    reactiveCircuitBreakerFactory.create("customer-service")
                            .run(it, throwable -> (Mono.just(UserDto.builder().username(username).build())))
                )
                .block();

        String productId = order.get().getProductId();
        ProductResponseDto productInfo = webClient
                .get()
                .uri(BASE_URI + "/catalog/product/get-product-id?productId={productId}", productId)
                .header(HEADER_AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("customer-service")
                                .run(it, throwable -> (Mono.just(ProductResponseDto.builder().id(productId).build())))
                )
                .block();


        return new OrderResponseDto(
                userInfo,
                productInfo,
                order.get().getQuantity(),
                order.get().getDeliveryComment(),
                order.get().getCreatedOn()
        );
    }

    private Order mapOrderDtoToOrder(OrderDto orderDto) {
        return Order.builder()
                .username(orderDto.username())
                .productId(orderDto.productId())
                .productName(orderDto.productName())
                .quantity(orderDto.quantity())
                .deliveryComment(orderDto.deliveryComment())
                .build();
    }
}
