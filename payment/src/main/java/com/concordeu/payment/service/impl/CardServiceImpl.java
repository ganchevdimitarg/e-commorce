package com.concordeu.payment.service.impl;

import com.concordeu.payment.dao.CardDao;
import com.concordeu.payment.dao.CustomerDao;
import com.concordeu.payment.domain.AppCard;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
import com.concordeu.payment.service.CardService;
import com.concordeu.payment.service.CustomerService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.Column;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cards
 * You can store multiple cards on a customer in order to charge the customer later.
 * You can also store multiple debit cards on a recipient in order to transfer to those cards later.
 * cardId: <a href="https://stripe.com/docs/api/cards">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService {
    private final CustomerService customerService;
    private final CardDao cardDao;
    @Value("${stripe.secret.key}")
    private String secretKey;

    /**
     * When you create a new credit card, you must specify a customer or recipient on which to create it.
     *
     * @param paymentDto card information
     * @return card id
     */
    @Override
    public PaymentDto createCard(PaymentDto paymentDto) {
        Stripe.apiKey = secretKey;

        List<String> expandList = new ArrayList<>();
        expandList.add("sources");

        Map<String, Object> retrieveParams = new HashMap<>();
        retrieveParams.put("expand", expandList);

        try {
            Customer customer = Customer.retrieve(
                    paymentDto.customerId(),
                    retrieveParams,
                    null
            );
            log.info("Method createCard: Get customer successful: {}", customer.getEmail());

            Map<String, Object> cardParams = new HashMap<>();
            cardParams.put("number", paymentDto.cardNumber());
            cardParams.put("exp_month", paymentDto.cardExpMonth());
            cardParams.put("exp_year", paymentDto.cardExpYear());
            cardParams.put("cvc", paymentDto.cardCvc());

            Map<String, Object> params = new HashMap<>();
            params.put("card", cardParams);

            Token token = Token.create(params);
            log.info("Method createCard: Create token successful: {}", token.getId());

            Map<String, Object> source = new HashMap<>();
            source.put("source", token.getId());

            Card card = (Card) customer.getSources().create(source);

            cardDao.saveAndFlush(AppCard.builder()
                    .cardId(card.getId())
                    .brand(card.getBrand())
                    .customerId(card.getCustomer())
                    .cvcCheck(card.getCvcCheck())
                    .expMonth(card.getExpMonth())
                    .expYear(card.getExpYear())
                    .lastFourDigits(card.getLast4())
                    .customer(customerService.findByUsername(customer.getName()))
                    .build());

            log.info("Method createCard: Create card successful: {}", card.getId());
            return PaymentDto.builder()
                    .cardId(card.getId())
                    .customerId(customer.getId())
                    .build();

        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
        }
    }

    /**
     * You can see a list of the cards belonging to a customer.
     * Note that the 10 most recent sources are always available on the Customer object.
     * If you need more than those 10, you can use this API method and the limit and
     * starting_after parameters to page through additional cards.
     *
     * @param username customer username (email)
     * @return ids of all cards owned by the customer
     */
    @Override
    public Set<String> getCards(String username) {
        Stripe.apiKey = secretKey;

        List<String> expandList = new ArrayList<>();
        expandList.add("sources");

        Map<String, Object> retrieveParams = new HashMap<>();
        retrieveParams.put("expand", expandList);

        try {
            Customer customer = Customer.retrieve(
                    customerService.getCustomerByUsername(username).getId(),
                    retrieveParams,
                    null
            );
            log.info("Method getCards: Get customer successful: {}", customer.getEmail());

            Map<String, Object> params = new HashMap<>();
            params.put("object", "card");
            params.put("limit", 3);

            PaymentSourceCollection cards = customer.getSources().list(params);
            log.info("Method getCards: Get ids of all cards owned by the customer");

            return cards.getData().stream().map(HasId::getId).collect(Collectors.toSet());

        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
        }
    }
}
