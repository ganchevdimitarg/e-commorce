package com.concordeu.payment.service;

import com.concordeu.payment.dto.CardDto;
import com.stripe.exception.StripeException;

public interface CardService {
    String createCard(CardDto cardDto) throws StripeException;
}
