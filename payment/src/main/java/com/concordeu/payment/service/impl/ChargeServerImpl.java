package com.concordeu.payment.service.impl;

import com.concordeu.payment.dao.ChargeDao;
import com.concordeu.payment.dto.ChargeDto;
import com.concordeu.payment.service.ChargeService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServerImpl implements ChargeService {
    private final ChargeDao chargeDao;

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Override
    public String createCharge(ChargeDto chargeDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("amount", chargeDto.amount());
        params.put("currency", chargeDto.currency());
        params.put("receipt_email", chargeDto.receiptEmail());
        params.put("customer", chargeDto.customerId());

        try {
            Charge charge = Charge.create(params);
            return charge.getStatus();
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }
}
