package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;

import java.util.Set;

public interface CardService {
    CardResponse createCard(String userId, CreateCardCommand command, String idempotencyKey);

    Set<String> getCards(String userId);

    Set<String> getCustomerCards(String userId);
}
