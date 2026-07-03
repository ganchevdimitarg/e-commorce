package com.ganchevdimitarg.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Convention-compliant wrapper around a Spring Data {@link Page} — never expose a raw
 * {@code Page<Entity>} from a controller.
 */
public record PageResponse<T>(List<T> content, int page, int size,
                               long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
