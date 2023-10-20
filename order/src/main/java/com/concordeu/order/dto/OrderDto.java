package com.concordeu.order.dto;

import com.concordeu.order.domain.Item;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record OrderDto(
        Long id,
        Long orderNumber,
        LocalDateTime createdOn,
        LocalDateTime updatedOn,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode,
        String cardNumber,
        Long cardExpMonth,
        Long cardExpYear,
        String cardCvc,
        String deliveryComment,
        ChargeDto charge,
        List<Item> items) {
}