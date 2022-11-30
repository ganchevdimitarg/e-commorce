package com.concordeu.order.dto;

public record OrderDto(
        String username,
        String productName,
        long quantity,
        String deliveryComment) {
}
