package com.concordeu.payment.service;

import com.concordeu.client.common.dto.CardDto;
import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.payment.domain.AppCard;
import com.stripe.exception.StripeException;

import java.util.List;
import java.util.Set;

public interface CardService {
    CardDto createCard(CardDto cardDto) throws StripeException;

    Set<String> getCards(String username);

    Set<String> getCustomerCards(String customerId);

    List<AppCard> findAppCardsByCustomerId(String customerId);
}
