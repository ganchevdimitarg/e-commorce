package com.concordeu.order.service;

import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.PaymentDto;

public interface ChargeService {
    void saveCharge(Order order, PaymentDto paymentCharge);
    PaymentDto makePayment(String cardId, String username, long amount);
}
