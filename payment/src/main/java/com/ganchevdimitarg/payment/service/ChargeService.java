package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.PaymentDto;

public interface ChargeService {
    PaymentDto createCharge(PaymentDto chargeDto);
}
