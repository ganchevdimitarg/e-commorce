package com.concordeu.order.service;

import com.concordeu.order.dao.ChargeDao;
import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeServiceImpl implements ChargeService {
    private final ChargeDao chargeDao;
    private final Gson mapper;
    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private static final String HEADER_AUTHORIZATION = "Authorization";
    @Value("${payment.service.customer.get.uri}")
    private String paymentCustomerGetUri;
    @Value("${payment.service.customer.post.uri}")
    private String paymentCustomerPostUri;
    @Value("${payment.service.card.post.uri}")
    private String paymentCardPostUri;
    @Value("${payment.service.charge.post.uri}")
    private String paymentChargePostUri;

    @Override
    public PaymentDto makePayment(OrderDto orderDto, String authorization, String authenticationName, long amount) {
        PaymentDto paymentCustomer = getCustomerFromPaymentService(
                paymentCustomerGetUri + authenticationName,
                authorization
        );

        String cardId = orderDto.cardId();

        if (paymentCustomer.customerId() == null) {
            paymentCustomer = createCustomer(authorization, authenticationName, authenticationName);
            cardId = addCardToCustomer(orderDto, authorization, paymentCustomer).cardId();
        }

        return chargeCustomer(authorization, amount, paymentCustomer, cardId);
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

    private PaymentDto chargeCustomer(String authorization, long amount, PaymentDto paymentCustomer, String cardId) {
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
                authorization,
                chargeRequestBody);
    }

    private PaymentDto addCardToCustomer(OrderDto orderDto, String authorization, PaymentDto paymentCustomer) {
        String cardRequestBody = mapper.toJson(PaymentDto.builder()
                .customerId(paymentCustomer.customerId())
                .number(orderDto.cardNumber())
                .expMonth(orderDto.cardExpMonth())
                .expYear(orderDto.cardExpYear())
                .cvc(orderDto.cardCvc())
                .build()
        );

        return sendRequestToPaymentService(
                paymentCardPostUri,
                authorization,
                cardRequestBody
        );
    }

    private PaymentDto createCustomer(String authorization, String username, String authenticationName) {
        String customerRequestBody = mapper.toJson(PaymentDto.builder()
                .username(username)
                .customerName(authenticationName)
                .build()
        );

        return sendRequestToPaymentService(
                paymentCustomerPostUri,
                authorization,
                customerRequestBody
        );
    }

    private PaymentDto sendRequestToPaymentService(String uri, String authorizationToken, String request) {
        return webClient
                .post()
                .uri(uri)
                .header(HEADER_AUTHORIZATION, authorizationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("charge-service")
                                .run(it, throwable -> (Mono.just(PaymentDto.builder().username("Ooops...").build())))
                )
                .block();
    }

    private PaymentDto getCustomerFromPaymentService(String uri, String authorizationToken) {
        return webClient
                .get()
                .uri(uri)
                .header(HEADER_AUTHORIZATION, authorizationToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("charge-service")
                                .run(it, throwable -> (Mono.just(PaymentDto.builder().username("Ooops...").build())))
                )
                .block();
    }


}
