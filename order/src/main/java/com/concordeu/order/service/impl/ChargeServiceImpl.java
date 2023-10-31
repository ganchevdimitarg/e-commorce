package com.concordeu.order.service.impl;

import com.concordeu.order.excaption.InvalidRequestDataException;
import com.concordeu.order.repositories.ChargeRepository;
import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.PaymentDto;
import com.concordeu.order.service.ChargeService;
import com.google.gson.Gson;
import io.micrometer.observation.annotation.Observed;
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
    private final ChargeRepository chargeRepository;
    private final Gson mapper;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

    @Value("${payment.service.customer.get.uri}")
    private String paymentServiceGetCustomerByUsernameUri;
    @Value("${payment.service.charge.post.uri}")
    private String paymentServiceChargeCustomerUri;

    @Override
    public Mono<PaymentDto> makePayment(String cardId, String authenticationName, long amount) {
        return webClient
                .get()
                .uri(paymentServiceGetCustomerByUsernameUri + authenticationName)
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
                .doOnError(throwable -> {
                    throw new InvalidRequestDataException("""
                            Something happened with the order service.
                            Please check the request details again
                            """);
                })
                .flatMap(paymentCustomer -> chargeCustomer(amount, paymentCustomer, cardId));
    }

    @Override
    public Mono<Charge> findChargeByOrderId(Long orderId) {
        return chargeRepository.findChargeByOrderId(orderId);
    }

    @Override
    public Mono<Charge> saveCharge(Order order, PaymentDto paymentCharge) {

        Charge charge = Charge.builder()
                .chargeId(paymentCharge.chargeId())
                .status(paymentCharge.chargeStatus())
                .orderId(order.getId())
                .currency(paymentCharge.currency())
                .amount(paymentCharge.amount())
                .build();

        log.info("Charge was successfully created");
        return chargeRepository.save(charge);
    }

    private Mono<PaymentDto> chargeCustomer(long amount, PaymentDto paymentCustomer, String cardId) {
        String chargeRequestBody = mapper.toJson(
                PaymentDto.builder()
                        .amount(amount)
                        .currency("usd")
                        .receiptEmail(paymentCustomer.username())
                        .customerId(paymentCustomer.customerId())
                        .username(paymentCustomer.username())
                        .cardId(cardId)
        );

        return webClient
                .post()
                .uri(paymentServiceChargeCustomerUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(chargeRequestBody)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("orderService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just(PaymentDto.builder().chargeId("").build());
                                })
                )
                .doOnError(throwable -> {
                    throw new InvalidRequestDataException("""
                            Something happened with the order service.
                            Please check the request details again
                            """);
                })
                .map(charge -> PaymentDto.builder()
                        .amount(amount)
                        .currency("usd")
                        .receiptEmail(paymentCustomer.username())
                        .customerId(paymentCustomer.customerId())
                        .username(paymentCustomer.username())
                        .cardId(cardId)
                        .chargeId(charge.chargeId())
                        .chargeStatus(charge.chargeStatus())
                        .build());
    }
}