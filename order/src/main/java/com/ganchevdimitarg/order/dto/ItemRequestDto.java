package com.ganchevdimitarg.order.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ItemRequestDto(
        List<String> items) {
}