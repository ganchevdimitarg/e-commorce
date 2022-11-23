package com.concordeu.order.dto;

import java.time.LocalDateTime;

public record OrderResponseDto(
        UserDto userInfo,
        ProductResponseDto productInfo,
        long quantity,
        String deliveryComment,
        LocalDateTime createdOn
) {
}
