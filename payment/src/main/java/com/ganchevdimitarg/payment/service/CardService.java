package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.PaymentDto;
import com.stripe.exception.StripeException;

import java.util.Set;

public interface CardService {
    PaymentDto createCard(PaymentDto cardDto) throws StripeException;

    Set<String> getCards(String username);

    Set<String> getCustomerCards(String customerId);
}
