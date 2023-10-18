package com.concordeu.order.dto;

import com.concordeu.order.domain.Order;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record ChargeDto(
        String id,
        String chargeId,
        String status,
        OffsetDateTime createOn,
        OffsetDateTime updateOn,
        Order order
) {}
