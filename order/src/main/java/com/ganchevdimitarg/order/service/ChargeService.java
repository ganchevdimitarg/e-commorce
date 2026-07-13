package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.PaymentDto;

public interface ChargeService {
    void saveCharge(Order order, PaymentDto paymentCharge);
    PaymentDto makePayment(String cardId, String username, long amount, String orderId);
    PaymentDto refund(String stripeChargeId, String username);
}
