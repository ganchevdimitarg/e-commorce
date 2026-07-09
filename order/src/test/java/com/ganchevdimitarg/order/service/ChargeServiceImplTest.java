package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ChargeDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceImplTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock
    private ChargeDao chargeDao;
    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    private CircuitBreaker circuitBreaker;

    private ChargeServiceImpl chargeService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        chargeService = new ChargeServiceImpl(chargeDao, builder.build(), circuitBreakerFactory);
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceGetCustomerByUsernameUri", "http://payment/customer?username=");
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceChargeCustomerUri", "http://payment/charge");
    }

    private void runSupplier() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Supplier<?> toRun = inv.getArgument(0);
            return toRun.get();
        });
    }

    @Test
    void should_saveCharge_when_paymentSucceeds() {
        Order order = Order.builder().username("john").orderNumber(1).build();
        PaymentDto payment = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        chargeService.saveCharge(order, payment);

        verify(chargeDao).save(any(Charge.class));
    }

    @Test
    void should_returnCharge_when_paymentServiceApproves() throws Exception {
        runSupplier();
        PaymentDto customer = PaymentDto.builder().username("john").customerId("cust_1").build();
        PaymentDto charged = PaymentDto.builder().chargeId("ch_1").chargeStatus("succeeded").build();

        server.expect(requestTo("http://payment/customer?username=john"))
                .andExpect(method(GET))
                .andRespond(withSuccess(json.writeValueAsString(customer), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://payment/charge"))
                .andExpect(method(POST))
                .andRespond(withSuccess(json.writeValueAsString(charged), MediaType.APPLICATION_JSON));

        PaymentDto result = chargeService.makePayment("card_1", "john", 1000L);

        assertThat(result.chargeId()).isEqualTo("ch_1");
        server.verify();
    }

    @Test
    void should_returnRefund_when_paymentServiceRefundsInFull() throws Exception {
        runSupplier();
        ReflectionTestUtils.setField(chargeService,
                "paymentServiceRefundChargeUri", "http://payment/refund");
        PaymentDto refunded = PaymentDto.builder().chargeId("ch_1").chargeStatus("refunded").build();

        server.expect(requestTo("http://payment/refund"))
                .andExpect(method(POST))
                .andRespond(withSuccess(json.writeValueAsString(refunded), MediaType.APPLICATION_JSON));

        PaymentDto result = chargeService.refund("ch_1", "john");

        assertThat(result.chargeId()).isEqualTo("ch_1");
        server.verify();
    }

    @Test
    void should_throwServiceUnavailable_when_paymentServiceUnavailable() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
            Function<Throwable, ?> fallback = inv.getArgument(1);
            return fallback.apply(new RuntimeException("payment down"));
        });

        assertThatThrownBy(() -> chargeService.makePayment("card_1", "john", 1000L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
