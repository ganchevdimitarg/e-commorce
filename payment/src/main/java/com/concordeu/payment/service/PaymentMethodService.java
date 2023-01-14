package com.concordeu.payment.service;

import com.concordeu.payment.dto.PaymentMethodDto;

public interface PaymentMethodService {
    String createPaymentMethod(PaymentMethodDto paymentMethodDto);
}
