package com.concordeu.payment.service.impl;

import com.concordeu.payment.repositories.ChargeRepository;
import com.concordeu.payment.repositories.CustomerRepository;
import com.concordeu.payment.entities.AppCharge;
import com.concordeu.payment.entities.AppCustomer;
import com.concordeu.payment.dto.PaymentDto;
import com.concordeu.payment.excaption.InvalidPaymentRequestException;
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
 * cardId: <a href="https://stripe.com/docs/api/charges">...</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServerImpl implements ChargeService {
    private final ChargeRepository chargeRepository;
    private final CustomerRepository customerRepository;
    @Value("${stripe.secret.key}")
    private String secretKey;

    /**
     * To charge a credit card or other payment cardId, you create a Charge object.
     * If your API key is in test mode, the supplied payment cardId (e.g., card) won’t actually be charged,
     * although everything else will occur as if in live mode.
     * (Stripe assumes that the charge would have completed successfully).
     *
     * @param paymentDto charge information
     * @return charge status: succeeded, pending, or failed
     */
    @Override
    public PaymentDto createCharge(PaymentDto paymentDto) {
        AppCustomer appCustomer = customerRepository.findByUsername(paymentDto.username()).orElseThrow(() -> {
            log.warn("Customer with username {} does not exist in db customers", paymentDto.username());
            throw new InvalidPaymentRequestException("Customer with username " + paymentDto.username() + " does not exist");
        });

        Stripe.apiKey = secretKey;

        Map<String, Object> params = new HashMap<>();
        params.put("amount", paymentDto.amount());
        params.put("currency", paymentDto.currency());
        params.put("receipt_email", appCustomer.getCards().get(0).getCustomer().getUsername());
        params.put("customer", appCustomer.getCustomerId());
        params.put("source", appCustomer.getCards().get(0).getCardId());

        try {
            Charge charge = Charge.create(params);

            chargeRepository.saveAndFlush(AppCharge.builder()
                    .chargeId(charge.getId())
                    .amount(charge.getAmount())
                    .currency(charge.getCurrency())
                    .customerId(charge.getCustomer())
                    .receiptEmail(charge.getReceiptEmail())
                    .customer(appCustomer)
                    .build());

            log.info("Method createCharge: Create successful charge: {}", charge.getId());
            return PaymentDto.builder()
                    .chargeId(charge.getId())
                    .chargeStatus(charge.getStatus())
                    .build();
        } catch (StripeException e) {
            log.warn(e.getMessage());
            throw new InvalidPaymentRequestException(e.getMessage());
        }
    }
}
