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
import com.ganchevdimitarg.payment.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cards
 * You can store multiple cards on a customer in order to charge the customer later.
 * cardId: <a href="https://stripe.com/docs/api/cards">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService {
    private final CardDao cardDao;
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;

    /**
     * Registers a new card for the given provider customer and links it locally.
     *
     * @param command card information
     * @return card id
     */
    @Override
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public CardResponse createCard(CreateCardCommand command) {
        GatewayCustomer stripeCustomer = paymentGateway.retrieveCustomer(command.customerId());
        GatewayCard card = paymentGateway.createCard(command.customerId(),
                new CardDetails(command.cardNumber(), command.cardExpMonth(),
                        command.cardExpYear(), command.cardCvc()));

        AppCustomer appCustomer = getAppCustomer(stripeCustomer.name());
        cardDao.saveAndFlush(AppCard.builder()
                .cardId(card.id())
                .brand(card.brand())
                .customerId(card.customerId())
                .cvcCheck(card.cvcCheck())
                .expMonth(card.expMonth())
                .expYear(card.expYear())
                .lastFourDigits(card.lastFourDigits())
                .customer(appCustomer)
                .build());

        log.info("Method createCard: Create card successful: {}", card.id());
        return new CardResponse(card.id(), command.customerId());
    }

    /**
     * Lists the provider card ids belonging to a customer.
     *
     * @param username customer username (email)
     * @return ids of all cards owned by the customer
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public Set<String> getCards(String username) {
        AppCustomer appCustomer = getAppCustomer(username);
        return paymentGateway.listCardIds(appCustomer.getCustomerId());
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public Set<String> getCustomerCards(String username) {
        return cardDao.findAppCardsByCustomerId(getAppCustomer(username).getCustomerId())
                .stream()
                .map(AppCard::getCardId)
                .collect(Collectors.toSet());
    }

    private AppCustomer getAppCustomer(String username) {
        return customerDao.findByUsername(username).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", username);
            return new NotFoundException("Customer", username);
        });
    }
}
