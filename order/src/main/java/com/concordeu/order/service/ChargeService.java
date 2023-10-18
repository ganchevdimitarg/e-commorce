package com.concordeu.order.service;

import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.ChargeDto;
import com.concordeu.order.dto.PaymentDto;
import reactor.core.publisher.Mono;

public interface ChargeService {
    Mono<Void> saveCharge(Order order, PaymentDto paymentCharge);
    Mono<PaymentDto> makePayment(String cardId, String username, long amount);
}
