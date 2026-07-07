package com.ganchevdimitarg.payment.gateway;

import java.util.Set;

/**
 * Abstraction over the external payment provider. Keeps the provider SDK
 * (Stripe today) out of the service layer, so services depend on this port
 * rather than concrete {@code com.stripe.*} types — enabling a second provider
 * and unit tests with a mocked gateway. Implementations translate provider
 * failures into {@code PaymentGatewayException}.
 */
public interface PaymentGateway {

    GatewayCustomer createCustomer(String email, String name);

    GatewayCustomer retrieveCustomer(String customerId);

    void deleteCustomer(String customerId);

    GatewayCard createCard(String customerId, CardDetails card);

    Set<String> listCardIds(String customerId);

    GatewayCharge createCharge(ChargeRequest request);
}
