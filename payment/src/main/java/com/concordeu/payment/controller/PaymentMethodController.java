package com.concordeu.payment.controller;

import com.concordeu.payment.dto.PaymentMethodDto;
import com.concordeu.payment.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/payment-method")
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodController {
    private final PaymentMethodService paymentMethodService;

    @PostMapping("/create-payment-method")
    public String createPaymentMethod(@RequestBody PaymentMethodDto paymentMethodDto) {
         return paymentMethodService.createPaymentMethod(paymentMethodDto);
    }
}
