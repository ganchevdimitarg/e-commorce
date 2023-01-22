package com.concordeu.order.service;

import com.concordeu.order.dao.ChargeDao;
import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.dto.PaymentDto;
import com.concordeu.order.excaption.InvalidRequestDataException;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServiceImpl implements ChargeService {
    private final ChargeDao chargeDao;
    private final Gson mapper;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    @Value("${payment.service.customer.get.uri}")
    private String paymentCustomerGetUri;
    @Value("${payment.service.charge.post.uri}")
    private String paymentChargePostUri;

    @Override
    public PaymentDto makePayment(String cardId, String authenticationName, long amount) {
        PaymentDto paymentCustomer = getCustomerFromPaymentService(
                paymentCustomerGetUri + authenticationName
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

    private PaymentDto chargeCustomer(long amount, PaymentDto paymentCustomer, String cardId) {
        String chargeRequestBody = mapper.toJson(
                PaymentDto.builder()
                        .amount(amount)
                        .currency("usd")
                        .receiptEmail(paymentCustomer.username())
                        .customerId(paymentCustomer.customerId())
                        .username(paymentCustomer.username())
                        .cardId(cardId)
        );

        return sendRequestToPaymentService(
                paymentChargePostUri,
                chargeRequestBody);
    }

    private PaymentDto sendRequestToPaymentService(String uri, String request) {
        PaymentDto paymentDto = webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just(PaymentDto.builder().chargeId("").build());
                                })
                )
                .block();

        checkPaymentServiceAvailability(paymentDto.chargeId());
        return paymentDto;
    }

    private PaymentDto getCustomerFromPaymentService(String uri) {
        PaymentDto paymentDto = webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just(PaymentDto.builder().username("").build());
                                })
                )
                .block();

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
