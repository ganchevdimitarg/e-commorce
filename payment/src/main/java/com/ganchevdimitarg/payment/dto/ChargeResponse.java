package com.ganchevdimitarg.payment.dto;

/** Response view returned after creating a charge. */
public record ChargeResponse(String chargeId, String chargeStatus) {
}
