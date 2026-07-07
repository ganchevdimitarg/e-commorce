package com.ganchevdimitarg.payment.gateway;

import com.ganchevdimitarg.payment.exception.PaymentGatewayException;
import com.stripe.exception.ApiException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Proves {@link StripePaymentGateway} never leaks the {@code com.stripe.*} SDK: any
 * {@link com.stripe.exception.StripeException} raised by the underlying call is
 * translated to {@link PaymentGatewayException} before it reaches the service layer.
 */
class StripePaymentGatewayTest {

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripePaymentGateway("sk_test_dummy");
        gateway.init();
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeChargeCreateThrowsStripeException() {
        ApiException stripeFailure = new ApiException("card was declined", "req_1", "card_declined", 402, null);

        try (MockedStatic<Charge> chargeMock = mockStatic(Charge.class)) {
            chargeMock.when(() -> Charge.create(any(Map.class), any(RequestOptions.class)))
                    .thenThrow(stripeFailure);

            ChargeRequest request = new ChargeRequest(500L, "usd", "john@doe.com", "cus_1", "card_1");

            assertThatThrownBy(() -> gateway.createCharge(request, "idem-1"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class)
                    .satisfies(ex -> assertThat(ex.getClass().getPackageName())
                            .isEqualTo("com.ganchevdimitarg.payment.exception"));
        }
    }

    @AfterEach
    void tearDown() {
        com.stripe.Stripe.apiKey = null;
    }
}
