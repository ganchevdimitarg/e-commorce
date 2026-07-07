package com.ganchevdimitarg.payment.service;

import com.ganchevdimitarg.payment.dto.ChargeResponse;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.dto.RefundChargeCommand;

public interface ChargeService {
    ChargeResponse createCharge(CreateChargeCommand command, String idempotencyKey);

    ChargeResponse refund(RefundChargeCommand command);
}
