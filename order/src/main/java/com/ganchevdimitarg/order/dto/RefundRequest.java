package com.ganchevdimitarg.order.dto;

/** Wire-format request body for payment's POST /api/v1/payment/charge/refund-charge. */
public record RefundRequest(String chargeId) {
}
