package com.concordeu.order.validation;

import com.concordeu.order.dto.OrderDto;

public interface ValidateRequest {
    boolean validateRequest(OrderDto orderDto);
}
