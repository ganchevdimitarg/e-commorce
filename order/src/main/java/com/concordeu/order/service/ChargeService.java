package com.concordeu.order.service;

import com.concordeu.order.domain.Charge;
import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.PaymentDto;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

public interface ChargeService {
    Mono<Charge> saveCharge(Order order, PaymentDto paymentCharge);
    Mono<PaymentDto> makePayment(String cardId, String username, long amount);
    Mono<Charge> findChargeByOrderId(Long orderId);
}
