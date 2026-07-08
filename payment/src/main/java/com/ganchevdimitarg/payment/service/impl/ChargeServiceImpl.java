package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.ChargeRequest;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import com.ganchevdimitarg.payment.gateway.GatewayRefund;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import com.ganchevdimitarg.payment.service.ChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Charges
 * To charge a credit or a debit card, you create a Charge object.
 * cardId: <a href="https://stripe.com/docs/api/charges">...</a>
 *
 * <p>The payer is always resolved from the gateway-authenticated {@code userId} (the
 * {@code X-User-Id} header) — never a caller-supplied id — so a caller can only charge
 * against their own customer, and can only refund charges that belong to it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServiceImpl implements ChargeService {
    private final ChargeDao chargeDao;
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;
    private final ChargePersistence chargePersistence;

    /**
     * Charges the authenticated user's card through the payment provider and records the
     * charge locally. The provider call is deliberately outside any DB transaction: it is
     * enrolled with an idempotency key so a client retry cannot double-charge, and persistence
     * is delegated to {@link ChargePersistence} so a DB-commit failure after a successful
     * charge cannot orphan the provider charge silently — the charge already exists at the
     * provider under a stable key. The payer's provider customer id is resolved from the
     * authenticated user, never trusted from the request body.
     *
     * @param userId         authenticated user id
     * @param command        charge information (card, amount, currency, receipt email)
     * @param idempotencyKey key passed through to the payment provider so retries dedupe
     * @return charge id and status
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse createCharge(String userId, CreateChargeCommand command, String idempotencyKey) {
        AppCustomer appCustomer = customerDao.findByUsername(userId).orElseThrow(() -> {
            log.warn("Customer for the authenticated user does not exist in db customers");
            return new NotFoundException("Customer", userId);
        });

        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot double-charge. The local row is written afterwards.
        GatewayCharge charge = paymentGateway.createCharge(
                new ChargeRequest(command.amount(), command.currency(), command.receiptEmail(),
                        appCustomer.getCustomerId(), command.cardId()),
                idempotencyKey);

        chargePersistence.persistCharge(charge, appCustomer);

        log.info("Method createCharge: Create successful charge: {}", charge.id());
        return new ChargeResponse(charge.id(), charge.status());
    }

    /**
     * Refunds a charge belonging to the authenticated user through the payment provider. Not
     * {@code @Transactional}: it performs no DB write — only reads then an outbound provider
     * call — so a transaction would wrap a network call for no benefit. The charge is verified
     * to belong to the caller's customer before the refund; a mismatch is reported as a 404 so
     * charge ids cannot be enumerated across customers. The refund carries an idempotency key
     * derived from the charge id (a full refund is naturally idempotent per charge), so an
     * automated compensation retry dedupes without needing a caller-supplied header.
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse refund(String userId, RefundChargeCommand command) {
        AppCustomer appCustomer = customerDao.findByUsername(userId).orElseThrow(() -> {
            log.warn("Customer for the authenticated user does not exist in db customers");
            return new NotFoundException("Customer", userId);
        });

        AppCharge charge = chargeDao.findByChargeId(command.chargeId())
                .filter(c -> c.getCustomerId().equals(appCustomer.getCustomerId()))
                .orElseThrow(() -> {
                    log.warn("Charge {} not found for the authenticated customer", command.chargeId());
                    return new NotFoundException("Charge", command.chargeId());
                });

        GatewayRefund refund = paymentGateway.refundCharge(
                charge.getChargeId(), "refund-" + charge.getChargeId());

        log.info("Method refund: refunded charge {} with status {}", command.chargeId(), refund.status());
        return new ChargeResponse(command.chargeId(), refund.status());
    }
}
