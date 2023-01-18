package com.concordeu.payment.service;

import com.concordeu.payment.dto.CardDto;
import com.concordeu.payment.dto.PaymentDto;
import com.stripe.exception.StripeException;

import java.util.Set;

public interface CardService {
    CardDto createCard(PaymentDto cardDto) throws StripeException;

    Set<String> getCards(String username);
}
