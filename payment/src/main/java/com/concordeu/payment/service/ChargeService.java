package com.concordeu.payment.service;

import com.concordeu.client.common.dto.PaymentDto;

public interface ChargeService {
    PaymentDto createCharge(PaymentDto chargeDto);
}
