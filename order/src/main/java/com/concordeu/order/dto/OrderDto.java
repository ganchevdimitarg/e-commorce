package com.concordeu.order.dto;

import java.time.LocalDateTime;

public record OrderDto(
        String username,
        String productName,
        long quantity,
        String deliveryComment,
        LocalDateTime createdOn) {
}
