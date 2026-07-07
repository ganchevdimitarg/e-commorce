package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardPersistenceTest {

    @Mock
    private CardDao cardDao;
    @InjectMocks
    private CardPersistence cardPersistence;

    @Test
    void should_mapGatewayCardToEntity_when_persisting() {
        GatewayCard gatewayCard = new GatewayCard("card_1", "visa", "cus_1", "pass", 12L, 2030L, "4242");
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();

        cardPersistence.persistCard(gatewayCard, customer);

        ArgumentCaptor<AppCard> captor = ArgumentCaptor.forClass(AppCard.class);
        verify(cardDao).saveAndFlush(captor.capture());
        AppCard persisted = captor.getValue();
        assertThat(persisted.getCardId()).isEqualTo("card_1");
        assertThat(persisted.getBrand()).isEqualTo("visa");
        assertThat(persisted.getCustomerId()).isEqualTo("cus_1");
        assertThat(persisted.getCvcCheck()).isEqualTo("pass");
        assertThat(persisted.getExpMonth()).isEqualTo(12L);
        assertThat(persisted.getExpYear()).isEqualTo(2030L);
        assertThat(persisted.getLastFourDigits()).isEqualTo("4242");
        assertThat(persisted.getCustomer()).isEqualTo(customer);
    }
}
