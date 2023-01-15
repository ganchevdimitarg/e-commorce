package com.concordeu.payment.service;

import com.concordeu.payment.dto.CardDto;
import com.stripe.exception.StripeException;

import java.util.Set;

public interface CardService {
    String createCard(CardDto cardDto) throws StripeException;

    Set<String> getCards(String username);
}
