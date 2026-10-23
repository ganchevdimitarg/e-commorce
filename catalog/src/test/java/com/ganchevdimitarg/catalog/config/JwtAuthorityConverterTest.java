package com.ganchevdimitarg.catalog.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class JwtAuthorityConverterTest {

    private static Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("t").header("alg", "none").subject("alice");
    }

    private static Collection<String> authoritiesFor(Jwt jwt) {
        JwtAuthenticationConverter converter = JwtAuthorityConverter.create();
        // Spring Security 7 appends FACTOR_BEARER to every converted token; it is not part
        // of the scope-to-authority mapping under test.
        return converter.convert(jwt).getAuthorities().stream()
                .filter(authority -> !(authority instanceof FactorGrantedAuthority))
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void should_mapScopesToScopePrefixedAuthorities_when_scopeClaimPresent() {
        Jwt jwt = jwtBuilder()
                .claim("scope", List.of("catalog.read", "catalog.review", "catalog.manage"))
                .build();

        assertThat(authoritiesFor(jwt))
                .containsExactlyInAnyOrder(
                        "SCOPE_catalog.read", "SCOPE_catalog.review", "SCOPE_catalog.manage");
    }

    @Test
    void should_preserveRoleAuthorities_when_scopeClaimContainsRolePrefixedValues() {
        Jwt jwt = jwtBuilder().claim("scope", List.of("catalog.read", "ROLE_ADMIN")).build();

        assertThat(authoritiesFor(jwt))
                .contains("SCOPE_catalog.read", "ROLE_ADMIN");
    }

    @Test
    void should_notAddRoleAuthority_when_scopeClaimAbsent() {
        Jwt jwt = jwtBuilder().build();

        assertThat(authoritiesFor(jwt)).isEmpty();
    }

    @Test
    void should_preserveRoleAuthorities_when_scopeClaimIsSpaceDelimitedString() {
        Jwt jwt = jwtBuilder().claim("scope", "catalog.read ROLE_ADMIN").build();

        assertThat(authoritiesFor(jwt))
                .contains("SCOPE_catalog.read", "ROLE_ADMIN");
    }

    @Test
    void should_mapRolesCollectionToRoleAuthorities_when_rolesClaimPresent() {
        Jwt jwt = jwtBuilder().claim("roles", List.of("ADMIN", "AUDITOR")).build();

        assertThat(authoritiesFor(jwt)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_AUDITOR");
    }

    @Test
    void should_mapSpaceDelimitedRolesString_when_rolesClaimPresent() {
        Jwt jwt = jwtBuilder().claim("roles", "ADMIN AUDITOR").build();

        assertThat(authoritiesFor(jwt)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_AUDITOR");
    }

    @Test
    void should_notDoublePrefixRoleValue_when_rolesClaimAlreadyHasRolePrefix() {
        Jwt jwt = jwtBuilder().claim("roles", List.of("ROLE_ADMIN")).build();

        assertThat(authoritiesFor(jwt)).contains("ROLE_ADMIN");
    }

    @Test
    void should_notDuplicateAuthority_whenScopeAndRolesBothCarryAdmin() {
        Jwt jwt = jwtBuilder()
                .claim("scope", List.of("catalog.read", "ROLE_ADMIN"))
                .claim("roles", List.of("ADMIN"))
                .build();

        assertThat(authoritiesFor(jwt)).containsOnlyOnce("ROLE_ADMIN");
    }
}
