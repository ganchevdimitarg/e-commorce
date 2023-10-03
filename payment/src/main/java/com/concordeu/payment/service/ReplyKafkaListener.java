package com.concordeu.payment.service;

import com.concordeu.payment.domain.AppCard;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
import com.concordeu.payment.repositories.CardRepository;
import com.concordeu.payment.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReplyKafkaListener {
    public static final String GET_CARDS_BY_USERNAME = "getRequestGetCardsByUsername";
    private final CardRepository cardRepository;
    private final CustomerRepository customerRepository;
    @KafkaListener(id = "server", topics = GET_CARDS_BY_USERNAME)
    @SendTo
    public String handle(String username) {
        /*Set<String> cards = cardRepository.findAppCardsByCustomerId(getAppCustomer(username).getCustomerId())
                .stream()
                .map(AppCard::getCardId)
                .collect(Collectors.toSet());
        System.out.println(cards.stream().findFirst().get());*/
        return "DAGOEBA";
    }
/*
    private AppCustomer getAppCustomer(String username) {
        AppCustomer appCustomer = customerRepository.findByUsername(username).orElseThrow(() ->
                new InvalidPaymentRequestException("Customer with username " + username + " does not exist"));
        return appCustomer;
    }*/
}
