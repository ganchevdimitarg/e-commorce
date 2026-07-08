package com.ganchevdimitarg.payment.controller;

import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;
import com.ganchevdimitarg.payment.service.CardService;
import jakarta.validation.Valid;
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
    public CardResponse createCard(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid CreateCardCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return cardService.createCard(userId, command, idempotencyKey);
    }

    @GetMapping("/get-cards")
    public Set<String> getCards(@RequestHeader("X-User-Id") String userId) {
        return cardService.getCards(userId);
    }

    @GetMapping("/get-customer-cards")
    public Set<String> getCustomerCards(@RequestHeader("X-User-Id") String userId) {
        return cardService.getCustomerCards(userId);
    }
}
