package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.ChargeRequest;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
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
    @InjectMocks
    private ChargeServiceImpl chargeService;

    private CreateChargeCommand command() {
        return new CreateChargeCommand("john@doe.com", "cus_1", "card_1", 500L, "usd", "john@doe.com");
    }

    @Test
    void should_chargeGatewayAndPersist_when_customerExists() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));
        when(paymentGateway.createCharge(any(ChargeRequest.class)))
                .thenReturn(new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded"));

        ChargeResponse response = chargeService.createCharge(command());

        assertThat(response).isEqualTo(new ChargeResponse("ch_1", "succeeded"));
        ArgumentCaptor<AppCharge> captor = ArgumentCaptor.forClass(AppCharge.class);
        verify(chargeDao).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getChargeId()).isEqualTo("ch_1");
        assertThat(captor.getValue().getAmount()).isEqualTo(500L);
    }

    @Test
    void should_throwNotFound_when_customerMissing() {
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.createCharge(command()))
                .isInstanceOf(NotFoundException.class);
    }
}
