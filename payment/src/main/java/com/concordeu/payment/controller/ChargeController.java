package com.concordeu.payment.controller;

import com.concordeu.client.common.dto.PaymentDto;
import com.concordeu.payment.service.ChargeService;
import io.micrometer.observation.annotation.Observed;
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
    @Observed(
            name = "user.name",
            contextualName = "createCharge",
            lowCardinalityKeyValues = {"method", "createCharge"}
    )
    public PaymentDto createCharge(@RequestBody PaymentDto paymentDto) {
        return chargeService.createCharge(paymentDto);
    }
}
