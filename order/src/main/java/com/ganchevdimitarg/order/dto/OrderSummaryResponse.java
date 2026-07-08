package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.OrderStatus;

import java.time.LocalDateTime;

/**
 * Summary projection of an order for the "list my orders" endpoint.
 */
public record OrderSummaryResponse(long orderNumber, OrderStatus status,
                                    String deliveryComment, LocalDateTime createdOn) {
}
