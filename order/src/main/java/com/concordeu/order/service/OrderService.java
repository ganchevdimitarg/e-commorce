package com.concordeu.order.service;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;
import org.springframework.security.core.Authentication;

public interface OrderService {
    void createOrder(OrderDto orderDao, String authenticationName);

    void deleteOrder(long orderNumber);

    OrderResponseDto getOrder(long orderNumber, String authenticationName);
}
