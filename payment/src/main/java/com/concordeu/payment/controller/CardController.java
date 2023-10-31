package com.concordeu.payment.controller;

import com.concordeu.client.common.dto.CardDto;
import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.payment.service.CardService;
import com.stripe.exception.StripeException;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/payment/card")
@RequiredArgsConstructor
@Slf4j
public class CardController {
    private final CardService cardService;

    @PostMapping("/create-card")
    @Observed(
            name = "user.name",
            contextualName = "createCard",
            lowCardinalityKeyValues = {"method", "createCard"}
    )
    public CardDto createCard(@RequestBody CardDto paymentDto) throws StripeException {
        return cardService.createCard(paymentDto);
    }
    @GetMapping("/get-cards")
    @Observed(
            name = "user.name",
            contextualName = "getCards",
            lowCardinalityKeyValues = {"method", "getCards"}
    )
    public Set<String> getCards(@RequestParam String username) {
        return cardService.getCards(username);
    }
    @GetMapping("/get-customer-cards")
    @Observed(
            name = "user.name",
            contextualName = "getCustomerCards",
            lowCardinalityKeyValues = {"method", "getCustomerCards"}
    )
    public Set<String> getCustomerCards(@RequestParam String username) {
        return cardService.getCustomerCards(username);
    }
}