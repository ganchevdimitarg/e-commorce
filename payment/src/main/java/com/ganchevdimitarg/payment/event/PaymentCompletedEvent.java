package com.ganchevdimitarg.payment.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Domain event emitted when a charge has been captured at the provider and recorded
 * locally. Consumed (out of this module's scope) by the order saga to advance an order
 * once its payment completes. {@code eventId} is stable per emission and doubles as the
 * {@code correlationId} header so consumers can dedupe.
 *
 * @param eventId    unique id for this emission (also the correlation id)
 * @param orderId    order the charge settles — the saga correlation key
 * @param chargeId   provider (Stripe) charge id
 * @param customerId provider (Stripe) customer id
 * @param amount     charged amount in the currency's minor units
 * @param currency   ISO-4217 currency code
 * @param status     provider charge status (e.g. {@code succeeded})
 * @param occurredAt when the charge completed
 */
public record PaymentCompletedEvent(
        String eventId,
        String orderId,
        String chargeId,
        String customerId,
        long amount,
        String currency,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
}
