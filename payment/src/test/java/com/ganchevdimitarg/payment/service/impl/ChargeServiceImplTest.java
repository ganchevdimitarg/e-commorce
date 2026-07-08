package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.ChargeRequest;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import com.ganchevdimitarg.payment.gateway.GatewayRefund;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private ChargeDao chargeDao;
    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private ChargePersistence chargePersistence;
    @InjectMocks
    private ChargeServiceImpl chargeService;

    private CreateChargeCommand command() {
        return new CreateChargeCommand("john@doe.com", "cus_1", "card_1", 500L, "usd", "john@doe.com");
    }

    @Test
    void should_chargeWithIdempotencyKeyAndPersist_when_customerExists() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));
        GatewayCharge gatewayCharge = new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded");
        when(paymentGateway.createCharge(any(ChargeRequest.class), eq("idem-123"))).thenReturn(gatewayCharge);

        ChargeResponse response = chargeService.createCharge(command(), "idem-123");

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        verify(paymentGateway).createCharge(any(ChargeRequest.class), eq("idem-123"));
        verify(chargePersistence).persistCharge(gatewayCharge, customer);
    }

    @Test
    void should_notCallGateway_when_customerMissing() {
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.createCharge(command(), "idem-123"))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).createCharge(any(), any());
        verify(chargePersistence, never()).persistCharge(any(), any());
    }

    @Test
    void should_refundKnownCharge_when_chargeExists() {
        AppCharge charge = AppCharge.builder().chargeId("ch_1").amount(500L).build();
        when(chargeDao.findByChargeId("ch_1")).thenReturn(Optional.of(charge));
        when(paymentGateway.refundCharge("ch_1", "refund-ch_1"))
                .thenReturn(new GatewayRefund("re_1", "ch_1", "succeeded"));

        ChargeResponse response = chargeService.refund(new RefundChargeCommand("ch_1"));

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        verify(paymentGateway).refundCharge("ch_1", "refund-ch_1");
    }

    @Test
    void should_throwNotFound_when_refundingUnknownCharge() {
        when(chargeDao.findByChargeId("ch_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.refund(new RefundChargeCommand("ch_missing")))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).refundCharge(any(), any());
    }
}
