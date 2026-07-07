package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a confirmed customer in its own transaction. Isolated on a separate bean so the
 * transactional boundary is honoured via the Spring proxy — the calling service invokes the
 * provider outside any transaction, then delegates here to write the local row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerPersistence {

    private final CustomerDao customerDao;

    @Transactional
    public CustomerResponse persistCustomer(GatewayCustomer customer) {
        customerDao.save(AppCustomer.builder()
                .customerId(customer.id())
                .username(customer.email())
                .customerName(customer.name())
                .build());
        log.info("Created customer in payment service db");
        return new CustomerResponse(customer.id(), customer.email(), customer.name());
    }
}
