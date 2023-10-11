package com.concordeu.client.common.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record ReplayPaymentDto(
        Set<CardDto> cards,
        String username,
        String notificationDto,
        String token,
        CardDto cardDto,
        PaymentDto paymentDto,
        UserRequestDto userRequestDto,
        String error) {
}
