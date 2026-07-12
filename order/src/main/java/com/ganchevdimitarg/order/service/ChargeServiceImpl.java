package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.dao.ChargeDao;
import com.ganchevdimitarg.order.domain.Charge;
import com.ganchevdimitarg.order.domain.Order;
import com.ganchevdimitarg.order.dto.ChargeRequest;
import com.ganchevdimitarg.order.dto.PaymentChargeResponse;
import com.ganchevdimitarg.order.dto.PaymentDto;
import com.ganchevdimitarg.order.dto.RefundRequest;
import com.ganchevdimitarg.order.exception.InvalidRequestDataException;
import com.ganchevdimitarg.order.exception.ServiceUnavailableException;
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

    private static final String PAYMENT_CIRCUIT_BREAKER = "order-payment";

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
    public PaymentDto makePayment(String cardId, String authenticationName, long amount, String orderId) {
        PaymentDto paymentCustomer = getCustomerFromPaymentService(
                paymentServiceGetCustomerByUsernameUri + authenticationName
        );

        PaymentDto chargeCustomer = chargeCustomer(amount, paymentCustomer, cardId, orderId);
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

    /**
     * Refund a charge in full. Compensation always returns the entire captured amount, so no
     * amount is sent — the payment service refunds the charge in full.
     */
    @Override
    public PaymentDto refund(String stripeChargeId, String username) {
        RefundRequest refundRequest = new RefundRequest(stripeChargeId);

        PaymentChargeResponse refunded = sendChargeRequestToPaymentService(paymentServiceRefundChargeUri, refundRequest);
        log.info("Refund went through successfully: {}", refunded.chargeId());
        return PaymentDto.builder().chargeId(refunded.chargeId()).chargeStatus(refunded.chargeStatus()).build();
    }

    private PaymentDto chargeCustomer(long amount, PaymentDto paymentCustomer, String cardId, String orderId) {
        ChargeRequest chargeRequest = new ChargeRequest(orderId, cardId, amount, "usd", paymentCustomer.username());

        PaymentChargeResponse response = sendChargeRequestToPaymentService(paymentServiceChargeCustomerUri, chargeRequest);
        return PaymentDto.builder().chargeId(response.chargeId()).chargeStatus(response.chargeStatus()).build();
    }

    /**
     * A tripped breaker or a failed call surfaces as a 503 {@link ServiceUnavailableException}
     * — never a silent empty sentinel that callers would mistake for a valid response.
     */
    private PaymentChargeResponse sendChargeRequestToPaymentService(String uri, Object request) {
        PaymentChargeResponse response = circuitBreakerFactory.create(PAYMENT_CIRCUIT_BREAKER).run(
                () -> restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(PaymentChargeResponse.class),
                throwable -> {
                    log.warn("Payment service unavailable", throwable);
                    throw new ServiceUnavailableException("Payment service is unavailable");
                });

        if (response == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        return response;
    }

    private PaymentDto getCustomerFromPaymentService(String uri) {
        PaymentDto paymentDto = circuitBreakerFactory.create(PAYMENT_CIRCUIT_BREAKER).run(
                () -> restClient
                        .get()
                        .uri(uri)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(PaymentDto.class),
                throwable -> {
                    log.warn("Payment service unavailable", throwable);
                    throw new ServiceUnavailableException("Payment service is unavailable");
                });

        if (paymentDto == null) {
            throw new InvalidRequestDataException("Payment service returned no response");
        }
        return paymentDto;
    }

}
