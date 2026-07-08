package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a confirmed charge in its own transaction. Isolated on a separate bean so the
 * transactional boundary is honoured via the Spring proxy — the calling service invokes the
 * provider outside any transaction, then delegates here to write the local row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargePersistence {

    private final ChargeDao chargeDao;

    @Transactional
    public void persistCharge(GatewayCharge charge, AppCustomer customer, String orderId) {
        chargeDao.saveAndFlush(AppCharge.builder()
                .chargeId(charge.id())
                .amount(charge.amount())
                .currency(charge.currency())
                .customerId(charge.customerId())
                .receiptEmail(charge.receiptEmail())
                .orderId(orderId)
                .customer(customer)
                .build());
        log.info("Persisted charge: {}", charge.id());
    }
}
