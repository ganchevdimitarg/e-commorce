package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;

public interface ChargeService {
    ChargeResponse createCharge(CreateChargeCommand command);
}
