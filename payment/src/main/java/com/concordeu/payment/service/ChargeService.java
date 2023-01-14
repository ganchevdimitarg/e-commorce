package com.concordeu.payment.service;

import com.concordeu.payment.dto.ChargeDto;

public interface ChargeService {
    String createCharge(ChargeDto chargeDto);
}
