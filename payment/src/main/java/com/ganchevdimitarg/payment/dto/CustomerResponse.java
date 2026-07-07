package com.ganchevdimitarg.payment.dto;

/** Response view of a customer. */
public record CustomerResponse(String customerId, String username, String customerName) {
}
