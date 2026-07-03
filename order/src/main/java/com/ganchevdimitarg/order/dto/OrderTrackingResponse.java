package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.OrderStatus;

import java.util.List;

public record OrderTrackingResponse(long orderNumber, OrderStatus currentStatus,
                                    List<StatusHistoryEntry> history) {
}
