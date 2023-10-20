package com.concordeu.order.service;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import reactor.core.publisher.Mono;

public interface OrderService {
    Mono<OrderDto> createOrder(OrderDto orderDto, String authenticationName);

    Mono<Void> deleteOrder(Long orderId);

    Mono<OrderResponseDto> getOrder(Long orderId, String authenticationName);
}
