package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    private static final String USER = "john@doe.com";

    @Mock
    private CardDao cardDao;
    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private CardPersistence cardPersistence;
    @InjectMocks
    private CardServiceImpl cardService;

    private CreateCardCommand command() {
        return new CreateCardCommand("tok_visa");
    }

    private AppCustomer customer() {
        return AppCustomer.builder().customerId("cus_1").username(USER).build();
    }

    @Test
    void should_registerTokenisedCardAgainstCallerCustomerAndPersist_when_createCard() {
        AppCustomer customer = customer();
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer));
        GatewayCard gatewayCard = new GatewayCard("card_1", "visa", "cus_1", "pass", 12L, 2030L, "4242");
        when(paymentGateway.createCard(eq("cus_1"), eq("tok_visa"), eq("idem-123")))
                .thenReturn(gatewayCard);

        CardResponse response = cardService.createCard(USER, command(), "idem-123");

        assertThat(response).isEqualTo(new CardResponse("card_1", "cus_1"));
        verify(paymentGateway).createCard("cus_1", "tok_visa", "idem-123");
        verify(cardPersistence).persistCard(gatewayCard, customer);
    }

    @Test
    void should_notCallGateway_when_customerMissing() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.createCard(USER, command(), "idem-123"))
                .isInstanceOf(NotFoundException.class);
        verify(paymentGateway, never()).createCard(any(), any(), any());
        verify(cardPersistence, never()).persistCard(any(), any());
    }

    @Test
    void should_delegateToGateway_when_getCards() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        when(paymentGateway.listCardIds("cus_1")).thenReturn(Set.of("card_1", "card_2"));

        assertThat(cardService.getCards(USER)).containsExactlyInAnyOrder("card_1", "card_2");
    }

    @Test
    void should_returnLocalCardIds_when_getCustomerCards() {
        when(customerDao.findByUsername(USER)).thenReturn(Optional.of(customer()));
        when(cardDao.findAppCardsByCustomerId("cus_1"))
                .thenReturn(List.of(AppCard.builder().cardId("card_1").build()));

        assertThat(cardService.getCustomerCards(USER)).containsExactly("card_1");
    }

    @Test
    void should_throwNotFound_when_customerMissingOnGetCards() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCards("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
