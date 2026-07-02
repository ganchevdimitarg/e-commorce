package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the client <em>read</em> path against the V2-seeded {@code gateway} client on a real
 * Postgres (Flyway-migrated, including the V3 audit columns). The dynamic {@code save()} path is a
 * known limitation of the over-normalised @ManyToMany model and is deferred — see decisions.md.
 */
class ClientServicePersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private ClientService clientService;

    @Test
    void should_loadFullyMappedClient_when_seededClientRequested() {
        RegisteredClient gateway = clientService.findByClientId("gateway");

        assertThat(gateway).isNotNull();
        assertThat(gateway.getClientId()).isEqualTo("gateway");
        assertThat(gateway.getScopes()).contains("catalog.read", "profile.read");
        assertThat(gateway.getRedirectUris()).isNotEmpty();
        assertThat(gateway.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(gateway.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void should_returnNull_when_clientUnknown() {
        assertThat(clientService.findByClientId("missing")).isNull();
    }
}
