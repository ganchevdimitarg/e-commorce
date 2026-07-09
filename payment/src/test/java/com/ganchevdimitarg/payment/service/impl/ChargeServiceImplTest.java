package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;
import com.ganchevdimitarg.payment.event.PaymentCompletedEvent;
import com.ganchevdimitarg.payment.event.PaymentEventPublisher;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.ChargeRequest;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import com.ganchevdimitarg.payment.gateway.GatewayRefund;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargeServiceImplTest {

    private static final String USER = "john@doe.com";

    @Mock
    private ChargeDao chargeDao;
    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private ChargePersistence chargePersistence;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @InjectMocks
    private ChargeServiceImpl chargeService;

    private CreateChargeCommand command() {
        return new CreateChargeCommand("order-1", "card_1", 500L, "usd", "john@doe.com");
    }

    private AppCustomer customer() {
        return AppCustomer.builder().customerId("cus_1").username(USER).build();
    }

    @Test
    void should_chargeAuthenticatedCustomerAndPersist_when_customerExists() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        GatewayCharge gatewayCharge = new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded");
        when(paymentGateway.createCharge(any(ChargeRequest.class), eq("idem-123"))).thenReturn(gatewayCharge);

        ChargeResponse response = chargeService.createCharge(USER, command(), "idem-123");

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        verify(paymentGateway).createCharge(any(ChargeRequest.class), eq("idem-123"));
        verify(chargePersistence).persistCharge(eq(gatewayCharge), any(AppCustomer.class), eq("order-1"));

        // The completed event carries the order and the settled provider charge.
        ArgumentCaptor<PaymentCompletedEvent> event = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentEventPublisher).publishCompleted(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo("order-1");
        assertThat(event.getValue().chargeId()).isEqualTo("ch_1");
        assertThat(event.getValue().status()).isEqualTo("succeeded");
    }

    @Test
    void should_notCallGatewayOrPublish_when_customerMissing() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.createCharge(USER, command(), "idem-123"))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).createCharge(any(), any());
        verify(chargePersistence, never()).persistCharge(any(), any(), any());
        verify(paymentEventPublisher, never()).publishCompleted(any());
    }

    @Test
    void should_refundOwnCharge_when_chargeBelongsToCaller() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        AppCharge charge = AppCharge.builder().chargeId("ch_1").customerId("cus_1").amount(500L).build();
        when(chargeDao.findByChargeId("ch_1")).thenReturn(Optional.of(charge));
        when(paymentGateway.refundCharge("ch_1", "refund-ch_1"))
                .thenReturn(new GatewayRefund("re_1", "ch_1", "succeeded"));

        ChargeResponse response = chargeService.refund(USER, new RefundChargeCommand("ch_1"));

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        verify(paymentGateway).refundCharge("ch_1", "refund-ch_1");
    }

    @Test
    void should_throwNotFoundAndNotRefund_when_chargeBelongsToAnotherCustomer() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        AppCharge othersCharge = AppCharge.builder().chargeId("ch_1").customerId("cus_OTHER").amount(500L).build();
        when(chargeDao.findByChargeId("ch_1")).thenReturn(Optional.of(othersCharge));

        assertThatThrownBy(() -> chargeService.refund(USER, new RefundChargeCommand("ch_1")))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).refundCharge(any(), any());
    }

    @Test
    void should_throwNotFound_when_refundingUnknownCharge() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        when(chargeDao.findByChargeId("ch_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.refund(USER, new RefundChargeCommand("ch_missing")))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).refundCharge(any(), any());
    }
}
