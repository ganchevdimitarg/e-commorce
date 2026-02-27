package com.ganchevdimitarg.order.validation;

import com.ganchevdimitarg.order.dto.OrderDto;

public interface ValidateRequest {
    boolean validateRequest(OrderDto orderDto);
}
