package com.concordeu.order.service;

import com.concordeu.order.dto.ChargeDto;
import com.concordeu.order.excaption.InvalidRequestDataException;
import com.concordeu.order.repositories.ChargeRepository;
import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.PaymentDto;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

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
    public Mono<Void> saveCharge(Order order, PaymentDto paymentCharge) {
        Charge charge = Charge.builder()
                .chargeId(paymentCharge.chargeId())
                .status(paymentCharge.chargeStatus())
                .order(order)
                .build();

        chargeRepository.save(charge);
        log.info("Charge was successfully created");
        return Mono.empty();
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
                });
    }
}