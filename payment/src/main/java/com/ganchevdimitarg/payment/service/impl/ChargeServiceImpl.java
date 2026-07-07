package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.ChargeRequest;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
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

    /**
     * Charges the given customer's card through the payment provider and records
     * the charge locally.
     *
     * @param command charge information
     * @return charge id and status
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public ChargeResponse createCharge(CreateChargeCommand command) {
        AppCustomer appCustomer = customerDao.findByUsername(command.username()).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", command.username());
            return new NotFoundException("Customer", command.username());
        });

        GatewayCharge charge = paymentGateway.createCharge(new ChargeRequest(
                command.amount(), command.currency(), command.receiptEmail(),
                command.customerId(), command.cardId()));

        chargeDao.saveAndFlush(AppCharge.builder()
                .chargeId(charge.id())
                .amount(charge.amount())
                .currency(charge.currency())
                .customerId(charge.customerId())
                .receiptEmail(charge.receiptEmail())
                .customer(appCustomer)
                .build());

        log.info("Method createCharge: Create successful charge: {}", charge.id());
        return new ChargeResponse(charge.id(), charge.status());
    }
}
