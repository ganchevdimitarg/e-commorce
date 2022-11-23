package com.concordeu.order.service;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.OrderResponseDto;

public interface OrderService {
    void createOrder(OrderDto orderDao);

    OrderDto deleteOrder(long orderNumber);

    OrderResponseDto getOrder(long orderNumber);
}
