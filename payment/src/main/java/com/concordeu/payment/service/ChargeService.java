package com.concordeu.payment.service;

import com.concordeu.payment.dto.ChargeDto;
import com.concordeu.payment.dto.PaymentDto;

public interface ChargeService {
    ChargeDto createCharge(PaymentDto chargeDto);
}
