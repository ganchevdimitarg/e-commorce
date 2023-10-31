package com.concordeu.payment.listener;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.CardDto;
import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import com.concordeu.payment.service.CardService;
import com.concordeu.payment.service.CustomerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import io.micrometer.observation.annotation.Observed;
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

    @KafkaListener(
            topics = Constant.CREATE_CUSTOMER,
            groupId = Constant.PAYMENT_SERVICE,
            containerFactory = CONTAINER_FACTORY
    )
    @SendTo
    @Observed(
            name = "user.name",
            contextualName = "handleCreateCustomer",
            lowCardinalityKeyValues = {"method", "handleCreateCustomer"}
    )
    public String handleCreateCustomer(ReplayPaymentDto replayPaymentDto) throws JsonProcessingException {
        String customerId = customerService.createCustomer(replayPaymentDto.userRequestDto());

        return getResponse(ReplayPaymentDto.builder()
                .paymentDto(PaymentDto.builder().customerId(customerId).build())
                .build());
    }

    @KafkaListener(
            topics = Constant.GET_CARDS_BY_USERNAME,
            groupId = Constant.PAYMENT_SERVICE,
            containerFactory = CONTAINER_FACTORY
    )
    @SendTo
    @Observed(
            name = "user.name",
            contextualName = "handleGetCardsByUsername",
            lowCardinalityKeyValues = {"method", "handleGetCardsByUsername"}
    )
    public String handleGetCardsByUsername(ReplayPaymentDto replayPaymentDto) throws JsonProcessingException {
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

    @KafkaListener(
            topics = Constant.ADD_CARD_TO_CUSTOMER,
            groupId = Constant.PAYMENT_SERVICE,
            containerFactory = CONTAINER_FACTORY
    )
    @SendTo
    @Observed(
            name = "user.name",
            contextualName = "handleAddCardToCustomer",
            lowCardinalityKeyValues = {"method", "handleAddCardToCustomer"}
    )
    public String handleAddCardToCustomer(ReplayPaymentDto replayPaymentDto) throws StripeException, JsonProcessingException {
        CardDto card = cardService.createCard(replayPaymentDto.cardDto());

        return getResponse(ReplayPaymentDto.builder()
                .cardDto(card)
                .build());
    }

    @KafkaListener(
            topics = Constant.DELETE_BY_USERNAME,
            groupId = Constant.PAYMENT_SERVICE,
            containerFactory = CONTAINER_FACTORY
    )
    @SendTo
    @Observed(
            name = "user.name",
            contextualName = "handleDeleteByUsername",
            lowCardinalityKeyValues = {"method", "handleDeleteByUsername"}
    )
    public void handleDeleteByUsername(ReplayPaymentDto replayPaymentDto) {
        customerService.deleteCustomer(replayPaymentDto.username());
    }

    private String getResponse(ReplayPaymentDto replayPaymentDto) throws JsonProcessingException {
        return objectMapper.writeValueAsString(replayPaymentDto);
    }
}
