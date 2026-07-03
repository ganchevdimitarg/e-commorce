package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.domain.OrderStatus;
import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.dto.OrderResponseDto;
import com.ganchevdimitarg.order.dto.OrderSummaryResponse;
import com.ganchevdimitarg.order.dto.OrderTrackingResponse;
import com.ganchevdimitarg.order.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

public interface OrderService {
    @PreAuthorize("hasAuthority('SCOPE_order.write')")
    void createOrder(OrderDto orderDao, String authenticationName);

    @PreAuthorize("hasAuthority('SCOPE_order.write')")
    void deleteOrder(long orderNumber);

    @PreAuthorize("hasAuthority('SCOPE_order.read')")
    OrderResponseDto getOrder(long orderNumber, String authenticationName);

    @PreAuthorize("hasAuthority('SCOPE_order.read')")
    PageResponse<OrderSummaryResponse> listMyOrders(String username, OrderStatus status, Pageable pageable);

    @PreAuthorize("hasAuthority('SCOPE_order.read')")
    OrderTrackingResponse getTracking(long orderNumber, String username);

    @PreAuthorize("hasAuthority('SCOPE_order.write')")
    void cancelOrder(long orderNumber, String username, String reason);

    @PreAuthorize("hasAuthority('SCOPE_order.admin')")
    void advanceStatus(long orderNumber, OrderStatus target, String changedBy, String reason);
}
