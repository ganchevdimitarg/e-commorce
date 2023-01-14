package com.concordeu.payment.service.impl;

import com.concordeu.payment.dto.PaymentMethodDto;
import com.concordeu.payment.service.PaymentMethodService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodServiceImpl implements PaymentMethodService {
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public String createPaymentMethod(PaymentMethodDto paymentMethodDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> card = new HashMap<>();
        card.put("number", paymentMethodDto.number());
        card.put("exp_month", paymentMethodDto.expMonth());
        card.put("exp_year", paymentMethodDto.expYear());
        card.put("cvc", paymentMethodDto.cvc());
        Map<String, Object> params = new HashMap<>();
        params.put("type", "card");
        params.put("card", card);

        try {
            PaymentMethod paymentMethod = PaymentMethod.create(params);
            return paymentMethod.getId();
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }
}