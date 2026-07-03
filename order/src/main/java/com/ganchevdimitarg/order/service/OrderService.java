package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    void createOrder(OrderDto orderDao, String authenticationName);

    void deleteOrder(long orderNumber);

    OrderResponseDto getOrder(long orderNumber, String authenticationName);

    PageResponse<OrderSummaryResponse> listMyOrders(String username, OrderStatus status, Pageable pageable);
}
