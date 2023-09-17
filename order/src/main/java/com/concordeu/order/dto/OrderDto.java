package com.concordeu.order.dto;

import com.concordeu.order.domain.Item;
import lombok.Builder;

import java.util.List;

@Builder
public record OrderDto(
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc,
        String deliveryComment,
        List<Item> items) {
}