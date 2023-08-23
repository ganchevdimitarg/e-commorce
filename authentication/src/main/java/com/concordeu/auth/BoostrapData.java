package com.concordeu.auth;

import com.concordeu.auth.entities.*;
import com.concordeu.auth.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class BoostrapData implements CommandLineRunner {
    private final ClientRepository repository;

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() == 0) {
            Client client = Client.builder()
                    .clientId("gateway")
                    .clientSecret("$2a$12$lmJlz3HawNWFHMhj2r2lo.iwj4fcPOtcxi..PXJWq.hfhybphafWG")
                    .authMethod("client_secret_basic")
                    .redirectUri(Set.of(
                            RedirectUri.builder()
                                    .redirectUri("http://127.0.0.1:8081/login/oauth2/code/gateway-client-oidc")
                                    .build(),
                            RedirectUri.builder()
                                    .redirectUri("http://127.0.0.1:8081/authorized")
                                    .build()
                    ))
                    .scope(Set.of(
                            Scope.builder().scope("catalog.read").build(),
                            Scope.builder().scope("catalog.write").build(),
                            Scope.builder().scope("profile.read").build(),
                            Scope.builder().scope("profile.write").build(),
                            Scope.builder().scope("order.read").build(),
                            Scope.builder().scope("order.write").build(),
                            Scope.builder().scope("notification.read").build()
                    ))
                    .tokenSettings(Set.of(
                            TokenSetting.builder()
                                    .accessTokenTimeToLive(600L)
                                    .refreshTokenTimeToLive(7200L)
                                    .build()
                    ))
                    .grantType(Set.of(
                            GrantType.builder().grantType("authorization_code").build(),
                            GrantType.builder().grantType("refresh_token").build(),
                            GrantType.builder().grantType("client_credentials").build()
                    ))
                    .build();

            repository.save(client);
        }
    }
}
