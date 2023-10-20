package com.concordeu.order.dto;

import com.concordeu.order.domain.Order;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Builder
public record ChargeDto(
        Long id,
        String chargeId,
        String status,
        LocalDateTime createOn,
        LocalDateTime updateOn,
        String amount,
        String currency,
        Long orderId
) {}
