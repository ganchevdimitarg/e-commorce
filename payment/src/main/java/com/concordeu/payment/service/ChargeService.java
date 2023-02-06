package com.concordeu.payment.service;

import com.concordeu.payment.dto.PaymentDto;

public interface ChargeService {
    PaymentDto createCharge(PaymentDto chargeDto);
}
