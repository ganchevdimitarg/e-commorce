package com.ganchevdimitarg.order.dto;

import jakarta.validation.Valid;
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
        @NotEmpty List<@Valid OrderLineDto> items) {

    // Redacts card data so the DTO can never leak PAN/CVC through logs or error output.
    // Full relocation of card fields out of this DTO is tracked as a coordinated follow-up.
    @Override
    public String toString() {
        return "OrderDto[username=%s, items=%s, deliveryComment=%s]"
                .formatted(username, items, deliveryComment);
    }
}
