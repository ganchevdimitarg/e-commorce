package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.OrderStatus;

import java.time.Instant;

public record StatusHistoryEntry(OrderStatus fromStatus, OrderStatus toStatus,
                                 String changedBy, String reason, Instant changedAt) {
}
