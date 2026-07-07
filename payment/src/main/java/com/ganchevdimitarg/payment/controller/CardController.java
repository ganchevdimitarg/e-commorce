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
    public CardResponse createCard(@RequestBody @Valid CreateCardCommand command) {
        return cardService.createCard(command);
    }

    @GetMapping("/get-cards")
    public Set<String> getCards(@RequestParam String username) {
        return cardService.getCards(username);
    }

    @GetMapping("/get-customer-cards")
    public Set<String> getCustomerCards(@RequestParam String username) {
        return cardService.getCustomerCards(username);
    }
}
