package com.ganchevdimitarg.payment.gateway;

import com.ganchevdimitarg.payment.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.HasId;
import com.stripe.model.PaymentSourceCollection;
import com.stripe.model.Token;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stripe-backed {@link PaymentGateway}. The only place in the module that touches
 * the {@code com.stripe.*} SDK; every provider failure is translated to
 * {@link PaymentGatewayException} so the service layer never sees a
 * {@link StripeException}.
 */
@Component
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final String secretKey;

    public StripePaymentGateway(@Value("${stripe.secret.key}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }

    @Override
    public GatewayCustomer createCustomer(String email, String name) {
        return call(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("email", email);
            params.put("name", name);
            Customer customer = Customer.create(params);
            log.info("Stripe createCustomer successful: {}", customer.getEmail());
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
    public GatewayCard createCard(String customerId, CardDetails card) {
        return call(() -> {
            Map<String, Object> retrieveParams = new HashMap<>();
            retrieveParams.put("expand", List.of("sources"));
            Customer customer = Customer.retrieve(customerId, retrieveParams, null);

            Map<String, Object> cardParams = new HashMap<>();
            cardParams.put("number", card.number());
            cardParams.put("exp_month", card.expMonth());
            cardParams.put("exp_year", card.expYear());
            cardParams.put("cvc", card.cvc());
            Token token = Token.create(Map.of("card", cardParams));

            Card created = (Card) customer.getSources().create(Map.of("source", token.getId()));
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
    public GatewayCharge createCharge(ChargeRequest request) {
        return call(() -> {
            Map<String, Object> params = new HashMap<>();
            params.put("amount", request.amount());
            params.put("currency", request.currency());
            params.put("receipt_email", request.receiptEmail());
            params.put("customer", request.customerId());
            params.put("source", request.source());
            Charge charge = Charge.create(params);
            log.info("Stripe createCharge successful: {}", charge.getId());
            return new GatewayCharge(charge.getId(), charge.getAmount(), charge.getCurrency(),
                    charge.getCustomer(), charge.getReceiptEmail(), charge.getStatus());
        });
    }

    private static GatewayCustomer toGatewayCustomer(Customer customer) {
        return new GatewayCustomer(customer.getId(), customer.getEmail(), customer.getName());
    }

    private <T> T call(StripeCall<T> stripeCall) {
        try {
            return stripeCall.get();
        } catch (StripeException e) {
            log.warn("Stripe call failed: {}", e.getMessage());
            throw new PaymentGatewayException(e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T get() throws StripeException;
    }
}
