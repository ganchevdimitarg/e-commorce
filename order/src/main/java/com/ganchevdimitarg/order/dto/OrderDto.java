package com.ganchevdimitarg.order.dto;

import com.ganchevdimitarg.order.domain.Item;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record OrderDto(
        @NotBlank String username,
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
        @NotEmpty List<Item> items) {
}
