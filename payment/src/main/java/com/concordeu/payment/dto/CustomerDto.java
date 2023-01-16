package com.concordeu.payment.dto;

import com.stripe.model.Customer;
import lombok.Builder;

@Builder
public record CustomerDto(
        String username,
        String customerName,
        String customerId) {
    public static CustomerDto getCustomer(Customer customer) {
        return CustomerDto.builder()
                .username(customer.getEmail())
                .customerName(customer.getName())
                .customerId(customer.getId())
                .build();
    }
}
