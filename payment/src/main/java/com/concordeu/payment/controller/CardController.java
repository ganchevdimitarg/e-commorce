package com.concordeu.payment.controller;

import com.concordeu.payment.dto.CardDto;
import com.concordeu.payment.service.CardService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/card")
@RequiredArgsConstructor
@Slf4j
public class CardController {
    private final CardService cardService;

    @PostMapping("/create-card")
    public String createCard(@RequestBody CardDto cardDto) throws StripeException {
        return cardService.createCard(cardDto);
    }
}
