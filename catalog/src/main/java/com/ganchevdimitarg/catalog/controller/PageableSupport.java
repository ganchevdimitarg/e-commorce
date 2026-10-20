package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Shared pagination guard for the catalog controllers. {@code @PageableDefault} supplies the default
 * size; this rejects oversized requests with a 400 (problem+json via {@code ControllerExceptionHandler})
 * rather than silently clamping, preserving the catalogue's documented max-100 contract. Each endpoint
 * passes the properties it can safely sort by, so an arbitrary client-supplied property never reaches
 * Spring Data as an unintended column.
 */
final class PageableSupport {

    static final int MAX_PAGE_SIZE = 100;

    private PageableSupport() {
    }

    static Pageable capped(Pageable pageable, Set<String> allowedSortProperties) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ValidationException("Page size must not exceed " + MAX_PAGE_SIZE);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedSortProperties.contains(order.getProperty())) {
                throw new ValidationException("Sort property not allowed: " + order.getProperty()
                        + "; allowed: " + allowedSortProperties.stream().sorted().toList());
            }
        }
        return pageable;
    }
}
