package com.concordeu.order.service;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import com.concordeu.order.dto.UserDto;
import reactor.core.publisher.Mono;

public interface OrderService {
    Mono<OrderDto> createOrder(OrderDto orderDto, String authenticationName);

    void deleteOrder(long orderNumber);

    Mono<OrderResponseDto> getOrder(long orderNumber, String authenticationName);
}
