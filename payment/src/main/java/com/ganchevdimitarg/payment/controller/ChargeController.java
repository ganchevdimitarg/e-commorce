package com.ganchevdimitarg.payment.controller;

import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;
import com.ganchevdimitarg.payment.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/charge")
@RequiredArgsConstructor
@Slf4j
public class ChargeController {
    private final ChargeService chargeService;

    @PostMapping("/create-charge")
    public ChargeResponse createCharge(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid CreateChargeCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return chargeService.createCharge(userId, command, idempotencyKey);
    }

    @PostMapping("/refund-charge")
    public ChargeResponse refundCharge(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid RefundChargeCommand command) {
        return chargeService.refund(userId, command);
    }
}
