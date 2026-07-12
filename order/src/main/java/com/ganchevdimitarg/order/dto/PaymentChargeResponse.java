package com.ganchevdimitarg.order.dto;

/** Wire-format response from payment's charge/refund endpoints. */
public record PaymentChargeResponse(String chargeId, String chargeStatus) {
}
