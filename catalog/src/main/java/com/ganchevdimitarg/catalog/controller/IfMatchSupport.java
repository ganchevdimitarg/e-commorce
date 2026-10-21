package com.ganchevdimitarg.catalog.controller;

import com.ganchevdimitarg.catalog.exception.ValidationException;

/**
 * Parses the {@code If-Match} request header for product updates. Catalog ETags are the
 * strong, opaque tags {@code "<n>"} derived from the product's optimistic-lock version.
 *
 * <p>A client that wants a precondition sends exactly one strong tag or {@code *}; a
 * comma-separated list of tags is deliberately not supported. Weak tags ({@code W/"n"}),
 * lists and non-numeric tags are rejected with a 400 rather than silently ignored, so a
 * client never believes its precondition was honoured when it was not.
 */
final class IfMatchSupport {

    private IfMatchSupport() {
    }

    /**
     * @return {@code null} when the header is absent, blank or {@code *} (no precondition);
     *         the version encoded in a single strong ETag; otherwise throws
     *         {@link ValidationException} for a weak, list-valued or non-numeric tag
     */
    static Long expectedVersion(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if ("*".equals(value)) {
            return null;
        }
        if (!value.startsWith("\"") || !value.endsWith("\"") || value.length() < 2) {
            throw new ValidationException("If-Match must be a strong ETag or *");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw new ValidationException("Malformed If-Match ETag");
        }
    }
}
