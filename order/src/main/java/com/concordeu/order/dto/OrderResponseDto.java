package com.concordeu.order.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record OrderResponseDto(
        UserDto userInfo,
        List<ProductResponseDto> productInfo,
        long orderNumber,
        String deliveryComment,
        OffsetDateTime createdOn) {
}
