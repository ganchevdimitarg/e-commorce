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
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
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
    public OrderResponseDto getOrder(long orderNumber, String authorization) {
        Optional<Order> order = orderDao.findByOrderNumber(orderNumber);
        if (order.isEmpty()) {
            log.warn("No such order");
            throw new IllegalArgumentException("No such order");
        }

        String base_uri = "http://127.0.0.1:8081/api/v1";
        String headerAuthorization = "Authorization";

        String username = order.get().getUsername();
        UserDto userInfo = webClient
                .get()
                .uri(base_uri + "/profile/get-by-username?username={username}", username)
                .header(headerAuthorization, authorization)
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
                .uri(base_uri + "/catalog/product/get-product-id?productId={productId}", productId)
                .header(headerAuthorization, authorization)
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
