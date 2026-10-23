package com.ganchevdimitarg.catalog.config;

/**
 * Authorities used by catalog's method security and OpenAPI scopes.
 *
 * <p>Scope names are a public contract: the auth service seeds them and API clients
 * request them. {@code catalog.write} no longer exists — a comment moderator must not be
 * able to reprice or delete products.
 */
public final class CatalogAuthorities {

    public static final String SCOPE_READ = "SCOPE_catalog.read";
    public static final String SCOPE_REVIEW = "SCOPE_catalog.review";
    public static final String SCOPE_MANAGE = "SCOPE_catalog.manage";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private CatalogAuthorities() {
    }
}
