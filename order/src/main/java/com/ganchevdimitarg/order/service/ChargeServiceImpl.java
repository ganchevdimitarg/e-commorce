package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ChargeDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServiceImpl implements ChargeService {
    private final ChargeDao chargeDao;
    private final RestClient restClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${payment.service.customer.get.uri}")
    private String paymentServiceGetCustomerByUsernameUri;
    @Value("${payment.service.charge.post.uri}")
    private String paymentServiceChargeCustomerUri;
    @Value("${payment.service.charge.refund.post.uri}")
    private String paymentServiceRefundChargeUri;

    @Override
    public PaymentDto makePayment(String cardId, String authenticationName, long amount) {
        PaymentDto paymentCustomer = getCustomerFromPaymentService(
                paymentServiceGetCustomerByUsernameUri + authenticationName
        );

        PaymentDto chargeCustomer = chargeCustomer(amount, paymentCustomer, cardId);
        log.info("Payment went through successfully: {}", chargeCustomer.chargeId());
        return chargeCustomer;
    }

    @Override
    public void saveCharge(Order order, PaymentDto paymentCharge) {
        Charge charge = Charge.builder()
                .chargeId(paymentCharge.chargeId())
                .status(paymentCharge.chargeStatus())
                .order(order)
                .build();

        chargeDao.save(charge);
        log.info("Charge was successfully created");
    }

    @Override
    public PaymentDto refund(String stripeChargeId, long amount, String username) {
        PaymentDto refundRequest = PaymentDto.builder()
                .chargeId(stripeChargeId)
                .amount(amount)
                .currency("usd")
                .username(username)
                .build();

        PaymentDto refunded = sendRequestToPaymentService(paymentServiceRefundChargeUri, refundRequest);
        log.info("Refund went through successfully: {}", refunded.chargeId());
        return refunded;
    }

    private PaymentDto chargeCustomer(long amount, PaymentDto paymentCustomer, String cardId) {
        PaymentDto chargeRequest = PaymentDto.builder()
                .amount(amount)
                .currency("usd")
                .receiptEmail(paymentCustomer.username())
                .customerId(paymentCustomer.customerId())
                .username(paymentCustomer.username())
                .cardId(cardId)
                .build();

        return sendRequestToPaymentService(paymentServiceChargeCustomerUri, chargeRequest);
    }

    private PaymentDto sendRequestToPaymentService(String uri, PaymentDto request) {
        PaymentDto paymentDto = circuitBreakerFactory.create("orderService").run(
                () -> restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(PaymentDto.class),
                throwable -> {
                    log.warn("Payment service is down", throwable);
                    return PaymentDto.builder().chargeId("").build();
                });

        if (paymentDto == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        checkPaymentServiceAvailability(paymentDto.chargeId());
        return paymentDto;
    }

    private PaymentDto getCustomerFromPaymentService(String uri) {
        PaymentDto paymentDto = circuitBreakerFactory.create("orderService").run(
                () -> restClient
                        .get()
                        .uri(uri)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(PaymentDto.class),
                throwable -> {
                    log.warn("Payment service is down", throwable);
                    return PaymentDto.builder().username("").build();
                });

        if (paymentDto == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        checkPaymentServiceAvailability(paymentDto.username());
        return paymentDto;
    }

    private void checkPaymentServiceAvailability(String token) {
        if (token.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the order service.
                    Please check the request details again
                    """);
        }
    }

}
