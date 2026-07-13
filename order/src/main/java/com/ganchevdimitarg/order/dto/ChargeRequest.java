package com.ganchevdimitarg.order.dto;

/** Wire-format request body for payment's POST /api/v1/payment/charge/create-charge. */
public record ChargeRequest(String orderId, String cardId, long amount, String currency, String receiptEmail) {
}
