package com.concordeu.payment.service;

import com.concordeu.client.common.constant.PaymentConstants;
import com.concordeu.client.common.dto.CardDto;
import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import com.concordeu.payment.domain.AppCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReplyKafkaListener {
    public static final String CONTAINER_FACTORY = "messageListener";

    private final CardService cardService;
    private final CustomerService customerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = PaymentConstants.CREATE_CUSTOMER, groupId = PaymentConstants.PAYMENT_SERVICE, containerFactory = CONTAINER_FACTORY)
    @SendTo
    public String handleCreateCustomer(ReplayPaymentDto replayPaymentDto) {
        String customerId = customerService.createCustomer(replayPaymentDto.userRequestDto());

        return getResponse(ReplayPaymentDto.builder()
                .paymentDto(PaymentDto.builder().customerId(customerId).build())
                .build());
    }

    @KafkaListener(topics = PaymentConstants.GET_CARDS_BY_USERNAME, groupId = PaymentConstants.PAYMENT_SERVICE, containerFactory = CONTAINER_FACTORY)
    @SendTo
    public String handleGetCardsByUsername(ReplayPaymentDto replayPaymentDto) {
        Set<CardDto> cards = cardService.findAppCardsByCustomerId(
                        customerService.findByUsername(replayPaymentDto.username()).getCustomerId())
                .stream()
                .map(c -> CardDto.builder()
                        .cardId(c.getCardId())
                        .customerId(c.getCustomerId())
                        .cardNumber(c.getCardNumber())
                        .cardExpMonth(c.getExpMonth())
                        .cardExpYear(c.getExpYear())
                        .cardCvc(c.getCvcCheck())
                        .build())
                .collect(Collectors.toSet());

        return getResponse(ReplayPaymentDto.builder()
                .username(replayPaymentDto.username())
                .cards(cards)
                .build());
    }

    @KafkaListener(topics = PaymentConstants.ADD_CARD_TO_CUSTOMER, groupId = PaymentConstants.PAYMENT_SERVICE, containerFactory = CONTAINER_FACTORY)
    @SendTo
    public String handleAddCardToCustomer(ReplayPaymentDto replayPaymentDto) throws StripeException {
        CardDto card = cardService.createCard(replayPaymentDto.cardDto());

        return getResponse(ReplayPaymentDto.builder()
                .cardDto(card)
                .build());
    }

    private String getResponse(ReplayPaymentDto replayPaymentDto) {
        try {
            return objectMapper.writeValueAsString(replayPaymentDto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
