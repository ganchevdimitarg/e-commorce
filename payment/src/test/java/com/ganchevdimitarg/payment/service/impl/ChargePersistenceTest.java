package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.ChargeDao;
import com.ganchevdimitarg.payment.domain.AppCharge;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCharge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChargePersistenceTest {

    @Mock
    private ChargeDao chargeDao;
    @InjectMocks
    private ChargePersistence chargePersistence;

    @Test
    void should_mapGatewayChargeToEntity_when_persisting() {
        GatewayCharge gatewayCharge = new GatewayCharge("ch_1", 500L, "usd", "cus_1", "john@doe.com", "succeeded");
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();

        chargePersistence.persistCharge(gatewayCharge, customer, "order-1");

        ArgumentCaptor<AppCharge> captor = ArgumentCaptor.forClass(AppCharge.class);
        verify(chargeDao).saveAndFlush(captor.capture());
        AppCharge persisted = captor.getValue();
        assertThat(persisted.getChargeId()).isEqualTo("ch_1");
        assertThat(persisted.getAmount()).isEqualTo(500L);
        assertThat(persisted.getCurrency()).isEqualTo("usd");
        assertThat(persisted.getCustomerId()).isEqualTo("cus_1");
        assertThat(persisted.getReceiptEmail()).isEqualTo("john@doe.com");
        assertThat(persisted.getOrderId()).isEqualTo("order-1");
        assertThat(persisted.getCustomer()).isEqualTo(customer);
    }
}
