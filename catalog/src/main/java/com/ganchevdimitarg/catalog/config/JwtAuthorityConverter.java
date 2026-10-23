package com.ganchevdimitarg.catalog.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps a validated JWT to catalog authorities.
 *
 * <p>Scopes come from the {@code scope} claim and are prefixed with {@code SCOPE_} by the
 * default {@link JwtGrantedAuthoritiesConverter}. Roles come from a dedicated {@code roles}
 * claim — a collection or a space-delimited string — and are exposed as {@code ROLE_*}
 * authorities, with the prefix added when the issuer omits it. Authorities are deduplicated,
 * so a value carried by both claims yields a single authority.
 *
 * <p>Before the auth service emitted a {@code roles} claim, {@code ROLE_*} values were folded
 * into {@code scope}; that path is kept deprecated for token compatibility.
 */
public final class JwtAuthorityConverter {

    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String CLAIM_DELIMITER = " ";

    private JwtAuthorityConverter() {
    }

    public static JwtAuthenticationConverter create() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
            claimValues(jwt, ROLES_CLAIM).stream()
                    .map(JwtAuthorityConverter::toRoleAuthority)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            legacyScopeRoles(jwt).stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            return authorities;
        });
        return converter;
    }

    private static String toRoleAuthority(String value) {
        return value.startsWith(ROLE_PREFIX) ? value : ROLE_PREFIX + value;
    }

    /**
     * @deprecated roles were folded into the {@code scope} claim before the auth service
     *     emitted a dedicated {@code roles} claim; kept for token compatibility. Remove once
     *     every issuer populates {@code roles} (catalog/TODO.md task 1.3).
     */
    @Deprecated(since = "2026-10-22", forRemoval = false)
    private static List<String> legacyScopeRoles(Jwt jwt) {
        return claimValues(jwt, SCOPE_CLAIM).stream()
                .filter(value -> value.startsWith(ROLE_PREFIX))
                .toList();
    }

    private static List<String> claimValues(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        // The auth service writes a set (Collection); the OAuth2 spec shape is a
        // space-delimited string. Normalise both before use.
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        if (claim instanceof String value) {
            return Arrays.stream(value.split(CLAIM_DELIMITER))
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        return List.of();
    }
}
