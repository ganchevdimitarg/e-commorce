package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status, String reason) {
}
