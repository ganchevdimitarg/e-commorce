package com.concordeu.order.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record OrderResponseDto(
        UserDto userInfo,
        List<ProductResponseDto> productInfo,
        long orderNumber,
        String deliveryComment,
        LocalDateTime createdOn) {
}
