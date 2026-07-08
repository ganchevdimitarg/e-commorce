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
    private final CustomerPersistence customerPersistence;

    /**
     * Creates the customer at the payment provider and records it locally. The provider
     * call is deliberately outside any DB transaction: it is enrolled with an idempotency
     * key so a client retry cannot create a duplicate provider customer, and persistence is
     * delegated to {@link CustomerPersistence} so a DB-commit failure after a successful
     * creation cannot orphan the provider customer silently — it already exists at the
     * provider under a stable key.
     *
     * @param command        customer information
     * @param idempotencyKey key passed through to the payment provider so retries dedupe
     * @return the persisted customer view
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public CustomerResponse createCustomer(CreateCustomerCommand command, String idempotencyKey) {
        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot create a duplicate provider customer. The local row is written
        // afterwards.
        GatewayCustomer customer = paymentGateway.createCustomer(
                command.username(), command.username(), idempotencyKey);

        return customerPersistence.persistCustomer(customer);
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
     * Deletes a customer at the provider and soft-deletes the local record. Not
     * {@code @Transactional}: the provider call runs OUTSIDE any transaction (mirroring the
     * create paths) so a DB transaction never wraps the network call. The soft-delete write is
     * delegated to {@link CustomerPersistence#softDelete(AppCustomer)} — a separate bean so the
     * transactional boundary is honoured via the Spring proxy, not bypassed by self-invocation.
     *
     * @param username customer username
     * @return the provider customer id that was deleted
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public String deleteCustomer(String username) {
        AppCustomer customer = customerDao.findByUsername(username).orElseThrow(() -> {
            logMessage(username);
            return new NotFoundException("Customer", username);
        });
        paymentGateway.deleteCustomer(customer.getCustomerId());
        customerPersistence.softDelete(customer);
        log.info("Delete customer successful: {}", customer.getUsername());
        return customer.getCustomerId();
    }
}
