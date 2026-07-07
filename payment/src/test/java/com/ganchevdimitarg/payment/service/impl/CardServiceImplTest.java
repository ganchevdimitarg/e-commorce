package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.CardDetails;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import com.ganchevdimitarg.payment.gateway.GatewayCustomer;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock
    private CardDao cardDao;
    @Mock
    private CustomerDao customerDao;
    @Mock
    private PaymentGateway paymentGateway;
    @InjectMocks
    private CardServiceImpl cardService;

    private CreateCardCommand command() {
        return new CreateCardCommand("cus_1", "4242424242424242", 12L, 2030L, "123");
    }

    @Test
    void should_registerCardAndLinkCustomer_when_createCard() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(paymentGateway.retrieveCustomer("cus_1"))
                .thenReturn(new GatewayCustomer("cus_1", "john@doe.com", "John"));
        when(paymentGateway.createCard(eq("cus_1"), any(CardDetails.class)))
                .thenReturn(new GatewayCard("card_1", "visa", "cus_1", "pass", 12L, 2030L, "4242"));
        when(customerDao.findByUsername("John")).thenReturn(Optional.of(customer));

        CardResponse response = cardService.createCard(command());

        assertThat(response).isEqualTo(new CardResponse("card_1", "cus_1"));
        verify(cardDao).saveAndFlush(any(AppCard.class));
    }

    @Test
    void should_delegateToGateway_when_getCards() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));
        when(paymentGateway.listCardIds("cus_1")).thenReturn(Set.of("card_1", "card_2"));

        assertThat(cardService.getCards("john@doe.com")).containsExactlyInAnyOrder("card_1", "card_2");
    }

    @Test
    void should_returnLocalCardIds_when_getCustomerCards() {
        AppCustomer customer = AppCustomer.builder().customerId("cus_1").username("john@doe.com").build();
        when(customerDao.findByUsername("john@doe.com")).thenReturn(Optional.of(customer));
        when(cardDao.findAppCardsByCustomerId("cus_1"))
                .thenReturn(List.of(AppCard.builder().cardId("card_1").build()));

        assertThat(cardService.getCustomerCards("john@doe.com")).containsExactly("card_1");
    }

    @Test
    void should_throwNotFound_when_customerMissing() {
        when(customerDao.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCards("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
