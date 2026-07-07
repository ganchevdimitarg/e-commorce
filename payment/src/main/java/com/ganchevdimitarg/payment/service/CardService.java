package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;

import java.util.Set;

public interface CardService {
    CardResponse createCard(CreateCardCommand command, String idempotencyKey);

    Set<String> getCards(String username);

    Set<String> getCustomerCards(String customerId);
}
