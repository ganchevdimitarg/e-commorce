package com.concordeu.payment.service.impl;

import com.concordeu.payment.dto.CardDto;
import com.concordeu.payment.dto.ChargeDto;
import com.concordeu.payment.service.CardService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Card;
import com.stripe.model.Customer;
import com.stripe.model.Token;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService {
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public String createCard(CardDto cardDto) throws StripeException {
        Stripe.apiKey = secretKey;


        Map<String, Object> retrieveParams = new HashMap<>();
        List<String> expandList = new ArrayList<>();
        expandList.add("sources");
        retrieveParams.put("expand", expandList);

        Customer customer = Customer.retrieve(
                cardDto.customerId(),
                retrieveParams,
                null
        );


        Map<String, Object> cardParams = new HashMap<>();
        cardParams.put("number", cardDto.number());
        cardParams.put("exp_month", cardDto.expMonth());
        cardParams.put("exp_year", cardDto.expYear());
        cardParams.put("cvc", cardDto.cvc());

        Map<String, Object> params = new HashMap<>();
        params.put("card", cardParams);

        Token token = Token.create(params);

        Map<String, Object> source = new HashMap<>();
        source.put("source", token.getId());

        Card card = (Card) customer.getSources().create(source);
        return card.getId();

    }
}
