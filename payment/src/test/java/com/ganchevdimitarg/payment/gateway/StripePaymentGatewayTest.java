package com.ganchevdimitarg.payment.gateway;

import com.ganchevdimitarg.payment.exception.PaymentGatewayException;
import com.stripe.exception.ApiException;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentSourceCollection;
import com.stripe.model.Refund;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Proves {@link StripePaymentGateway} never leaks the {@code com.stripe.*} SDK: any
 * {@link com.stripe.exception.StripeException} raised by the underlying call is
 * translated to {@link PaymentGatewayException} before it reaches the service layer,
 * and that every provider-neutral {@code Gateway*} record is mapped correctly from the
 * corresponding Stripe SDK model on the happy path.
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

    @Test
    void should_returnMappedCharge_when_stripeChargeCreateSucceeds() {
        Charge stripeCharge = mock(Charge.class);
        when(stripeCharge.getId()).thenReturn("ch_1");
        when(stripeCharge.getAmount()).thenReturn(500L);
        when(stripeCharge.getCurrency()).thenReturn("usd");
        when(stripeCharge.getCustomer()).thenReturn("cus_1");
        when(stripeCharge.getReceiptEmail()).thenReturn("john@doe.com");
        when(stripeCharge.getStatus()).thenReturn("succeeded");

        try (MockedStatic<Charge> chargeMock = mockStatic(Charge.class)) {
            chargeMock.when(() -> Charge.create(any(Map.class), any(RequestOptions.class)))
                    .thenReturn(stripeCharge);

            ChargeRequest request = new ChargeRequest(500L, "usd", "john@doe.com", "cus_1", "card_1");
            GatewayCharge result = gateway.createCharge(request, "idem-1");

            assertThat(result).isEqualTo(new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded"));
        }
    }

    @Test
    void should_returnMappedCustomer_when_stripeCustomerCreateSucceeds() {
        Customer stripeCustomer = mock(Customer.class);
        when(stripeCustomer.getId()).thenReturn("cus_1");
        when(stripeCustomer.getEmail()).thenReturn("john@doe.com");
        when(stripeCustomer.getName()).thenReturn("John Doe");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.create(any(Map.class), any(RequestOptions.class)))
                    .thenReturn(stripeCustomer);

            GatewayCustomer result = gateway.createCustomer("john@doe.com", "John Doe", "idem-1");

            assertThat(result).isEqualTo(new GatewayCustomer("cus_1", "john@doe.com", "John Doe"));
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeCustomerCreateThrowsStripeException() {
        ApiException stripeFailure = new ApiException("invalid request", "req_2", "invalid_request", 400, null);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.create(any(Map.class), any(RequestOptions.class)))
                    .thenThrow(stripeFailure);

            assertThatThrownBy(() -> gateway.createCustomer("john@doe.com", "John Doe", "idem-1"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @Test
    void should_returnMappedCustomer_when_stripeCustomerRetrieveSucceeds() {
        Customer stripeCustomer = mock(Customer.class);
        when(stripeCustomer.getId()).thenReturn("cus_2");
        when(stripeCustomer.getEmail()).thenReturn("jane@doe.com");
        when(stripeCustomer.getName()).thenReturn("Jane Doe");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("cus_2")).thenReturn(stripeCustomer);

            GatewayCustomer result = gateway.retrieveCustomer("cus_2");

            assertThat(result).isEqualTo(new GatewayCustomer("cus_2", "jane@doe.com", "Jane Doe"));
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeCustomerRetrieveThrowsStripeException() {
        ApiException stripeFailure = new ApiException("no such customer", "req_3", "resource_missing", 404, null);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("missing")).thenThrow(stripeFailure);

            assertThatThrownBy(() -> gateway.retrieveCustomer("missing"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @Test
    void should_deleteCustomer_when_stripeCustomerRetrieveAndDeleteSucceed() throws Exception {
        Customer stripeCustomer = mock(Customer.class);
        when(stripeCustomer.delete()).thenReturn(stripeCustomer);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("cus_3")).thenReturn(stripeCustomer);

            gateway.deleteCustomer("cus_3");
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeCustomerDeleteThrowsStripeException() {
        ApiException stripeFailure = new ApiException("no such customer", "req_4", "resource_missing", 404, null);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("missing")).thenThrow(stripeFailure);

            assertThatThrownBy(() -> gateway.deleteCustomer("missing"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @Test
    void should_returnMappedCard_when_stripeCardCreateSucceeds() throws Exception {
        Customer stripeCustomer = mock(Customer.class);
        PaymentSourceCollection sources = mock(PaymentSourceCollection.class);
        Token token = mock(Token.class);
        Card createdCard = mock(Card.class);

        when(stripeCustomer.getSources()).thenReturn(sources);
        when(token.getId()).thenReturn("tok_1");
        when(sources.create(anyMap(), any(RequestOptions.class))).thenReturn(createdCard);
        when(createdCard.getId()).thenReturn("card_1");
        when(createdCard.getBrand()).thenReturn("visa");
        when(createdCard.getCustomer()).thenReturn("cus_4");
        when(createdCard.getCvcCheck()).thenReturn("pass");
        when(createdCard.getExpMonth()).thenReturn(12L);
        when(createdCard.getExpYear()).thenReturn(2030L);
        when(createdCard.getLast4()).thenReturn("4242");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             MockedStatic<Token> tokenMock = mockStatic(Token.class)) {
            customerMock.when(() -> Customer.retrieve("cus_4", Map.of("expand", List.of("sources")), null))
                    .thenReturn(stripeCustomer);
            tokenMock.when(() -> Token.create(any(Map.class))).thenReturn(token);

            CardDetails cardDetails = new CardDetails("4242424242424242", 12L, 2030L, "123");
            GatewayCard result = gateway.createCard("cus_4", cardDetails, "idem-1");

            assertThat(result).isEqualTo(new GatewayCard("card_1", "visa", "cus_4", "pass", 12L, 2030L, "4242"));
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeCardCreateThrowsStripeException() {
        ApiException stripeFailure = new ApiException("no such customer", "req_5", "resource_missing", 404, null);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("missing", Map.of("expand", List.of("sources")), null))
                    .thenThrow(stripeFailure);

            CardDetails cardDetails = new CardDetails("4242424242424242", 12L, 2030L, "123");

            assertThatThrownBy(() -> gateway.createCard("missing", cardDetails, "idem-1"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @Test
    void should_returnCardIds_when_stripeCustomerRetrieveAndListSucceed() throws Exception {
        Customer stripeCustomer = mock(Customer.class);
        PaymentSourceCollection sources = mock(PaymentSourceCollection.class);
        PaymentSourceCollection listResult = mock(PaymentSourceCollection.class);
        Card card1 = mock(Card.class);
        Card card2 = mock(Card.class);

        when(stripeCustomer.getSources()).thenReturn(sources);
        when(card1.getId()).thenReturn("card_1");
        when(card2.getId()).thenReturn("card_2");
        when(listResult.getData()).thenReturn(List.of(card1, card2));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("cus_5", Map.of("expand", List.of("sources")), null))
                    .thenReturn(stripeCustomer);
            when(sources.list(anyMap())).thenReturn(listResult);

            Set<String> result = gateway.listCardIds("cus_5");

            assertThat(result).containsExactlyInAnyOrder("card_1", "card_2");
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeListCardIdsThrowsStripeException() {
        ApiException stripeFailure = new ApiException("no such customer", "req_6", "resource_missing", 404, null);

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.retrieve("missing", Map.of("expand", List.of("sources")), null))
                    .thenThrow(stripeFailure);

            assertThatThrownBy(() -> gateway.listCardIds("missing"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @Test
    void should_returnMappedRefund_when_stripeRefundCreateSucceeds() {
        Refund stripeRefund = mock(Refund.class);
        when(stripeRefund.getId()).thenReturn("re_1");
        when(stripeRefund.getCharge()).thenReturn("ch_1");
        when(stripeRefund.getStatus()).thenReturn("succeeded");

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(Map.class), any(RequestOptions.class)))
                    .thenReturn(stripeRefund);

            GatewayRefund result = gateway.refundCharge("ch_1", "refund-ch_1");

            assertThat(result).isEqualTo(new GatewayRefund("re_1", "ch_1", "succeeded"));
        }
    }

    @Test
    void should_translateToPaymentGatewayException_when_stripeRefundCreateThrowsStripeException() {
        ApiException stripeFailure = new ApiException("no such charge", "req_7", "resource_missing", 404, null);

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(Map.class), any(RequestOptions.class)))
                    .thenThrow(stripeFailure);

            assertThatThrownBy(() -> gateway.refundCharge("missing", "refund-missing"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasCauseInstanceOf(com.stripe.exception.StripeException.class);
        }
    }

    @AfterEach
    void tearDown() {
        com.stripe.Stripe.apiKey = null;
    }
}
