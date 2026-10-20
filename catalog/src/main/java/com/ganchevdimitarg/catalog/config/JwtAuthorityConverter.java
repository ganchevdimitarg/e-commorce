package com.ganchevdimitarg.catalog.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Maps a validated JWT to catalog authorities.
 *
 * <p>The auth service writes the {@code scope} claim as a set of strings, and a moderator
 * token carries {@code ROLE_ADMIN} alongside the scopes. The default
 * {@link JwtGrantedAuthoritiesConverter} prefixes every value with {@code SCOPE_}, so
 * {@code ROLE_ADMIN} would become {@code SCOPE_ROLE_ADMIN} and never match a role check.
 * This converter keeps the default mapping and additionally exposes every {@code ROLE_*}
 * scope value verbatim.
 */
public final class JwtAuthorityConverter {

    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_DELIMITER = " ";

    private JwtAuthorityConverter() {
    }

    public static JwtAuthenticationConverter create() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            scopeValues(jwt).stream()
                    .filter(value -> value.startsWith(ROLE_PREFIX))
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            return authorities;
        });
        return converter;
    }

    private static List<String> scopeValues(Jwt jwt) {
        Object claim = jwt.getClaims().get(SCOPE_CLAIM);
        // The auth service writes a set (Collection); the OAuth2 spec shape is a
        // space-delimited string. Normalise both before filtering.
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        if (claim instanceof String value) {
            return Arrays.stream(value.split(SCOPE_DELIMITER))
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        return List.of();
    }
}
