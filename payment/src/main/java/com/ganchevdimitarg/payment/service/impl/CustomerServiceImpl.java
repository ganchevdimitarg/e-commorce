package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCustomer;
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
 *
 * <p>Identity is always the gateway-authenticated {@code userId} (the {@code X-User-Id}
 * header value) — never a caller-supplied parameter — so a caller can only ever act on
 * their own customer record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;
    private final CustomerPersistence customerPersistence;

    /**
     * Creates the authenticated user's customer at the payment provider and records it
     * locally. The provider call is deliberately outside any DB transaction: it is enrolled
     * with an idempotency key so a client retry cannot create a duplicate provider customer,
     * and persistence is delegated to {@link CustomerPersistence} so a DB-commit failure
     * after a successful creation cannot orphan the provider customer silently — it already
     * exists at the provider under a stable key.
     *
     * @param userId         authenticated user id (doubles as the provider email)
     * @param idempotencyKey key passed through to the payment provider so retries dedupe
     * @return the persisted customer view
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public CustomerResponse createCustomer(String userId, String idempotencyKey) {
        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot create a duplicate provider customer. The local row is written
        // afterwards.
        GatewayCustomer customer = paymentGateway.createCustomer(userId, userId, idempotencyKey);

        return customerPersistence.persistCustomer(customer);
    }

    /**
     * Retrieves the authenticated user's own customer record.
     *
     * @param userId authenticated user id
     * @return the persisted customer view
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public CustomerResponse getCurrentCustomer(String userId) {
        AppCustomer appCustomer = customerDao.findByUsername(userId).orElseThrow(() -> {
            logMissingCustomer();
            return new NotFoundException("Customer", userId);
        });
        return new CustomerResponse(appCustomer.getCustomerId(), appCustomer.getUsername(),
                appCustomer.getCustomerName());
    }

    private static void logMissingCustomer() {
        log.warn("Customer for the authenticated user does not exist in db customers");
    }

    /**
     * Deletes the authenticated user's customer at the provider and soft-deletes the local
     * record. Not {@code @Transactional}: the provider call runs OUTSIDE any transaction
     * (mirroring the create paths) so a DB transaction never wraps the network call. The
     * soft-delete write is delegated to {@link CustomerPersistence#softDelete(AppCustomer)} —
     * a separate bean so the transactional boundary is honoured via the Spring proxy, not
     * bypassed by self-invocation.
     *
     * @param userId authenticated user id
     * @return the provider customer id that was deleted
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public String deleteCustomer(String userId) {
        AppCustomer customer = customerDao.findByUsername(userId).orElseThrow(() -> {
            logMissingCustomer();
            return new NotFoundException("Customer", userId);
        });
        paymentGateway.deleteCustomer(customer.getCustomerId());
        customerPersistence.softDelete(customer);
        log.info("Delete customer successful");
        return customer.getCustomerId();
    }
}
