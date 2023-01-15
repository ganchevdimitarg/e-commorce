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

/**
 * Charges
 * To charge a credit or a debit card, you create a Charge object.
 * You can retrieve and refund individual charges as well as list all charges.
 * Charges are identified by a unique, random ID.
 * source: <a href="https://stripe.com/docs/api/charges">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServerImpl implements ChargeService {
    private final ChargeDao chargeDao;

    @Value("${stripe.secret.key}")
    private String secretKey;

    /**
     * To charge a credit card or other payment source, you create a Charge object.
     * If your API key is in test mode, the supplied payment source (e.g., card) won’t actually be charged,
     * although everything else will occur as if in live mode.
     * (Stripe assumes that the charge would have completed successfully).
     *
     * @param chargeDto charge information
     * @return charge status: succeeded, pending, or failed
     */
    @Override
    public String createCharge(ChargeDto chargeDto) {
        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("amount", chargeDto.amount());
        params.put("currency", chargeDto.currency());
        params.put("receipt_email", chargeDto.receiptEmail());
        params.put("customer", chargeDto.customerId());
        params.put("source", chargeDto.source());

        try {
            Charge charge = Charge.create(params);
            return charge.getStatus();
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }
}
