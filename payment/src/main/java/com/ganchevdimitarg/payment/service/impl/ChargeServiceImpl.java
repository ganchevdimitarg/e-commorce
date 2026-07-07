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
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges
 * To charge a credit or a debit card, you create a Charge object.
 * cardId: <a href="https://stripe.com/docs/api/charges">...</a>
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
     * Charges the given customer's card through the payment provider and records
     * the charge locally. The provider call is deliberately outside any DB
     * transaction: it is enrolled with an idempotency key so a client retry cannot
     * double-charge, and persistence is delegated to {@link ChargePersistence} so a
     * DB-commit failure after a successful charge cannot orphan the provider charge
     * silently — the charge already exists at the provider under a stable key.
     *
     * @param command        charge information
     * @param idempotencyKey key passed through to the payment provider so retries dedupe
     * @return charge id and status
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse createCharge(CreateChargeCommand command, String idempotencyKey) {
        AppCustomer appCustomer = customerDao.findByUsername(command.username()).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", command.username());
            return new NotFoundException("Customer", command.username());
        });

        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot double-charge. The local row is written afterwards.
        GatewayCharge charge = paymentGateway.createCharge(
                new ChargeRequest(command.amount(), command.currency(), command.receiptEmail(),
                        command.customerId(), command.cardId()),
                idempotencyKey);

        chargePersistence.persistCharge(charge, appCustomer);

        log.info("Method createCharge: Create successful charge: {}", charge.id());
        return new ChargeResponse(charge.id(), charge.status());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse refund(RefundChargeCommand command) {
        AppCharge charge = chargeDao.findByChargeId(command.chargeId())
                .orElseThrow(() -> {
                    log.warn("Charge {} does not exist in db charges", command.chargeId());
                    return new NotFoundException("Charge", command.chargeId());
                });

        GatewayRefund refund = paymentGateway.refundCharge(charge.getChargeId());

        log.info("Method refund: refunded charge {} with status {}", command.chargeId(), refund.status());
        return new ChargeResponse(command.chargeId(), refund.status());
    }
}
