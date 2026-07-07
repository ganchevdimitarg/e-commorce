package com.ganchevdimitarg.payment.dto;

/** Response view returned after registering a card. */
public record CardResponse(String cardId, String customerId) {
}
