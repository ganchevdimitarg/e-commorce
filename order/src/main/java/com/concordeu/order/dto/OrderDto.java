package com.concordeu.order.dto;

import com.concordeu.order.domain.Item;

import java.util.List;

public record OrderDto(
        String username,
        String deliveryComment,
        List<Item> items,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc,
        String cardId) {
}