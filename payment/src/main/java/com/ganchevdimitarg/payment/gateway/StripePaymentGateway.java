package com.ganchevdimitarg.payment.gateway;

import com.ganchevdimitarg.payment.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.HasId;
import com.stripe.model.PaymentSourceCollection;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Stripe-backed {@link PaymentGateway}. The only place in the module that touches
 * the {@code com.stripe.*} SDK; every provider failure is translated to
 * {@link PaymentGatewayException} so the service layer never sees a
 * {@link StripeException}.
 *
 * <p>Every outbound call is guarded (root CLAUDE.md mandate): a {@link CircuitBreaker}
 * sheds load when Stripe is unavailable and a {@link Semaphore} bulkhead caps concurrent
 * in-flight calls so a provider slowdown cannot exhaust the request pool. Card declines
 * are normal traffic and deliberately do <em>not</em> trip the breaker.
 */
@Component
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private static final int BULKHEAD_MAX_CONCURRENT = 20;
    private static final int TIMEOUT_MS = 5_000;

    private final String secretKey;
    private final CircuitBreaker circuitBreaker;
    private final Semaphore bulkhead = new Semaphore(BULKHEAD_MAX_CONCURRENT);

    public StripePaymentGateway(@Value("${stripe.secret.key}") String secretKey,
                                CircuitBreakerRegistry registry) {
        this.secretKey = secretKey;
        this.circuitBreaker = registry.circuitBreaker("stripe", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .recordException(StripePaymentGateway::isInfrastructureFailure)
                .build());
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
        Stripe.setConnectTimeout(TIMEOUT_MS);
        Stripe.setReadTimeout(TIMEOUT_MS);
    }

    @Override
    public GatewayCustomer createCustomer(String email, String name, String idempotencyKey) {
        return call(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("email", email);
            params.put("name", name);
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            Customer customer = Customer.create(params, options);
            log.info("Stripe createCustomer successful: {}", customer.getId());
            return toGatewayCustomer(customer);
        });
    }

    @Override
    public GatewayCustomer retrieveCustomer(String customerId) {
        return call(() -> toGatewayCustomer(Customer.retrieve(customerId)));
    }

    @Override
    public void deleteCustomer(String customerId) {
        call(() -> {
            Customer.retrieve(customerId).delete();
            log.info("Stripe deleteCustomer successful: {}", customerId);
            return null;
        });
    }

    @Override
    public GatewayCard createCard(String customerId, String sourceToken, String idempotencyKey) {
        return call(() -> {
            // sourceToken is a client-side-tokenised Stripe token/source id (e.g. from
            // Stripe.js/Elements). No raw PAN/CVC ever reaches this service.
            Customer customer = Customer.retrieve(customerId);
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            Card created = (Card) customer.getSources().create(Map.of("source", sourceToken), options);
            log.info("Stripe createCard successful: {}", created.getId());
            return new GatewayCard(created.getId(), created.getBrand(), created.getCustomer(),
                    created.getCvcCheck(), created.getExpMonth(), created.getExpYear(), created.getLast4());
        });
    }

    @Override
    public Set<String> listCardIds(String customerId) {
        return call(() -> {
            Map<String, Object> retrieveParams = new HashMap<>();
            retrieveParams.put("expand", List.of("sources"));
            Customer customer = Customer.retrieve(customerId, retrieveParams, null);

            Map<String, Object> params = new HashMap<>();
            params.put("object", "card");
            params.put("limit", 3);
            PaymentSourceCollection cards = customer.getSources().list(params);
            return cards.getData().stream().map(HasId::getId).collect(Collectors.toSet());
        });
    }

    @Override
    public GatewayCharge createCharge(ChargeRequest request, String idempotencyKey) {
        return call(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", request.amount());
            params.put("currency", request.currency());
            params.put("receipt_email", request.receiptEmail());
            params.put("customer", request.customerId());
            params.put("source", request.source());
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            Charge charge = Charge.create(params, options);
            log.info("Stripe createCharge successful: {}", charge.getId());
            return new GatewayCharge(charge.getId(), charge.getAmount(), charge.getCurrency(),
                    charge.getCustomer(), charge.getReceiptEmail(), charge.getStatus());
        });
    }

    @Override
    public GatewayRefund refundCharge(String chargeId, String idempotencyKey) {
        return call(() -> {
            // No amount => Stripe refunds the charge in full, which is what a
            // compensating refund requires. The idempotency key (derived from the charge id)
            // lets an automated compensation retry dedupe without a caller-supplied header.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            Refund refund = Refund.create(Map.of("charge", chargeId), options);
            log.info("Stripe refundCharge successful: {}", refund.getId());
            return new GatewayRefund(refund.getId(), refund.getCharge(), refund.getStatus());
        });
    }

    private static GatewayCustomer toGatewayCustomer(Customer customer) {
        return new GatewayCustomer(customer.getId(), customer.getEmail(), customer.getName());
    }

    /**
     * Card declines and other business errors are normal traffic and must not open the
     * breaker; only infrastructure/availability failures count towards the failure rate.
     */
    private static boolean isInfrastructureFailure(Throwable throwable) {
        Throwable cause = (throwable instanceof PaymentGatewayException) ? throwable.getCause() : throwable;
        return !(cause instanceof CardException);
    }

    private <T> T call(StripeCall<T> stripeCall) {
        if (!bulkhead.tryAcquire()) {
            log.warn("Stripe bulkhead full ({} concurrent); shedding call", BULKHEAD_MAX_CONCURRENT);
            throw new PaymentGatewayException("Payment provider temporarily saturated");
        }
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return stripeCall.get();
                } catch (StripeException e) {
                    log.warn("Stripe call failed: {}", e.getMessage());
                    throw new PaymentGatewayException(e.getMessage(), e);
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Stripe circuit '{}' open: {}", circuitBreaker.getName(), e.getMessage());
            throw new PaymentGatewayException("Payment provider temporarily unavailable", e);
        } finally {
            bulkhead.release();
        }
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T get() throws StripeException;
    }
}
