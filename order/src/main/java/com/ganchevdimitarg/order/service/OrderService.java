package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;

public interface OrderService {
    void createOrder(OrderDto orderDao, String authenticationName);

    void deleteOrder(long orderNumber);

    OrderResponseDto getOrder(long orderNumber, String authenticationName);
}
