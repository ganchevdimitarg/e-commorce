package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.springframework.data.domain.Pageable;

/**
 * Shared pagination guard for the catalog controllers. {@code @PageableDefault} supplies the default
 * size; this rejects oversized requests with a 400 (problem+json via {@code ControllerExceptionHandler})
 * rather than silently clamping, preserving the catalogue's documented max-100 contract.
 */
final class PageableSupport {

    static final int MAX_PAGE_SIZE = 100;

    private PageableSupport() {
    }

    static Pageable capped(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ValidationException("Page size must not exceed " + MAX_PAGE_SIZE);
        }
        return pageable;
    }
}
