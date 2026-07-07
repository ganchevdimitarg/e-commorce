package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a confirmed card in its own transaction. Isolated on a separate bean so the
 * transactional boundary is honoured via the Spring proxy — the calling service invokes the
 * provider outside any transaction, then delegates here to write the local row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardPersistence {

    private final CardDao cardDao;

    @Transactional
    public void persistCard(GatewayCard card, AppCustomer customer) {
        cardDao.saveAndFlush(AppCard.builder()
                .cardId(card.id())
                .brand(card.brand())
                .customerId(card.customerId())
                .cvcCheck(card.cvcCheck())
                .expMonth(card.expMonth())
                .expYear(card.expYear())
                .lastFourDigits(card.lastFourDigits())
                .customer(customer)
                .build());
        log.info("Method createCard: Create card successful: {}", card.id());
    }
}
