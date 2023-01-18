package com.concordeu.payment.controller;

import com.concordeu.payment.dto.ChargeDto;
import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.service.ChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/charge")
@RequiredArgsConstructor
@Slf4j
public class ChargeController {
    private final ChargeService chargeService;

    @PostMapping("/create-charge")
    public PaymentDto createCharge(@RequestBody PaymentDto paymentDto) {
        ChargeDto charge = chargeService.createCharge(paymentDto);
        return PaymentDto.builder()
                .chargeId(charge.id())
                .chargeStatus(charge.status())
                .build();
    }
}
