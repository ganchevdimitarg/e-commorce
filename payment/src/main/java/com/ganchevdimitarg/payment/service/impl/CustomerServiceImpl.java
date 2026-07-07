package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CreateCustomerCommand;
import com.ganchevdimitarg.payment.dto.CustomerResponse;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import com.ganchevdimitarg.payment.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Customers
 * This object represents a customer of your business.
 * It lets you create recurring charges and track payments that belong to the same customer.
 * cardId: <a href="https://stripe.com/docs/api/customers">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public CustomerResponse createCustomer(CreateCustomerCommand command) {
        GatewayCustomer customer = paymentGateway.createCustomer(command.username(), command.username());

        customerDao.save(AppCustomer.builder()
                .customerId(customer.id())
                .username(customer.email())
                .customerName(customer.name())
                .build());
        log.info("Created customer in payment service db");

        return new CustomerResponse(customer.id(), customer.email(), customer.name());
    }

    /**
     * Retrieves a Customer object.
     *
     * @param username customer username
     * @return the persisted customer view
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public CustomerResponse getCustomerByUsername(String username) {
        AppCustomer appCustomer = customerDao.findByUsername(username).orElseThrow(() -> {
            logMessage(username);
            return new NotFoundException("Customer", username);
        });
        return new CustomerResponse(appCustomer.getCustomerId(), appCustomer.getUsername(),
                appCustomer.getCustomerName());
    }

    private static void logMessage(String username) {
        log.warn("Customer with username {} does not exist in db customers", username);
    }

    /**
     * Deletes a customer at the provider and soft-deletes the local record.
     *
     * @param username customer username
     * @return the provider customer id that was deleted
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public String deleteCustomer(String username) {
        AppCustomer customer = customerDao.findByUsername(username).orElseThrow(() -> {
            logMessage(username);
            return new NotFoundException("Customer", username);
        });
        paymentGateway.deleteCustomer(customer.getCustomerId());
        customer.setDeletedAt(Instant.now());
        customerDao.save(customer);
        log.info("Delete customer successful: {}", customer.getUsername());
        return customer.getCustomerId();
    }
}
