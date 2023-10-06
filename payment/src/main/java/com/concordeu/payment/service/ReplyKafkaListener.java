package com.concordeu.payment.service;

import com.concordeu.client.common.dto.ReplayPaymentDto;
import com.concordeu.payment.domain.AppCard;
import com.concordeu.payment.domain.AppCustomer;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
import com.concordeu.payment.repositories.CardRepository;
import com.concordeu.payment.repositories.CustomerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public static final String PAYMENT_SERVICE = "paymentService";
    private final CardRepository cardRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;
    @KafkaListener(topics = GET_CARDS_BY_USERNAME, groupId = PAYMENT_SERVICE, containerFactory = "messageListener")
    @SendTo
    public String handle(ReplayPaymentDto paymentDto) {
        System.out.println();
        Set<String> cards = cardRepository.findAppCardsByCustomerId(getAppCustomer(paymentDto.username()).getCustomerId())
                .stream()
                .map(AppCard::getCardId)
                .collect(Collectors.toSet());

        try {
            ReplayPaymentDto userCards = ReplayPaymentDto.builder()
                    .username(paymentDto.username())
                    .cards(cards)
                    .build();
            return objectMapper.writeValueAsString(userCards);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    private AppCustomer getAppCustomer(String username) {
        return customerRepository.findByUsername(username).orElseThrow(() ->
                new InvalidPaymentRequestException("Customer with username " + username + " does not exist"));
    }
}
