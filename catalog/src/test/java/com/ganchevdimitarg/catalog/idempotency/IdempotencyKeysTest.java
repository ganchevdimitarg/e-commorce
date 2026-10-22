package com.ganchevdimitarg.catalog.idempotency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class IdempotencyKeysTest {

    private static final String PREFIX = "catalog:idempotency:";
    private static final String URI = "/api/v1/catalog/products";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_produceSameKey_when_samePrincipalMethodPathAndClientKey() {
        String first = IdempotencyKeys.scopedKey("alice", "POST", URI, "k1");
        String second = IdempotencyKeys.scopedKey("alice", "POST", URI, "k1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_produceDifferentKey_when_principalDiffers() {
        assertThat(IdempotencyKeys.scopedKey("alice", "POST", URI, "k1"))
                .isNotEqualTo(IdempotencyKeys.scopedKey("bob", "POST", URI, "k1"));
    }

    @Test
    void should_produceDifferentKey_when_methodDiffers() {
        assertThat(IdempotencyKeys.scopedKey("alice", "POST", URI, "k1"))
                .isNotEqualTo(IdempotencyKeys.scopedKey("alice", "PUT", URI, "k1"));
    }

    @Test
    void should_produceDifferentKey_when_pathDiffers() {
        assertThat(IdempotencyKeys.scopedKey("alice", "POST", URI, "k1"))
                .isNotEqualTo(IdempotencyKeys.scopedKey("alice", "POST", "/api/v1/catalog/categories", "k1"));
    }

    @Test
    void should_prefixWithCatalogIdempotency_andHashTheSuffix() {
        String key = IdempotencyKeys.scopedKey("alice", "POST", URI, "client-secret");

        assertThat(key).startsWith(PREFIX);
        String suffix = key.substring(PREFIX.length());
        assertThat(suffix).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(key).doesNotContain("alice").doesNotContain("client-secret");
    }

    @Test
    void should_returnPrincipalName_when_authenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", "n/a", "SCOPE_catalog.manage"));

        assertThat(IdempotencyKeys.principalName()).isEqualTo("alice");
    }

    @Test
    void should_returnAnonymous_when_noAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(IdempotencyKeys.principalName()).isEqualTo("anonymous");
    }

    @Test
    void should_returnAnonymous_when_anonymousAuthenticationToken() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(IdempotencyKeys.principalName()).isEqualTo("anonymous");
    }
}
