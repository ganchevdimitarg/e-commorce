package com.concordeu.auth.config.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.config.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {
    private final KeyManager keyManager;
    private final PasswordEncoder passwordEncoder;

    public AuthorizationServerConfig(KeyManager keyManager, PasswordEncoder passwordEncoder) {
        this.keyManager = keyManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${gateway.client.secret}")
    private String gatewayClientSecret;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http
                .exceptionHandling()
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .and()
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient gatewayClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("gateway")
                .clientSecret(gatewayClientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8081/login/oauth2/code/gateway-client-oidc")
                .redirectUri("http://127.0.0.1:8081/authorized")
                .scope(OidcScopes.OPENID)
                .scope("catalog.read")
                .scope("catalog.write")
                .scope("profile.read")
                .scope("profile.write")
                .scope("order.read")
                .scope("order.write")
                .scope("notification.read")
                .scope("notification.write")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .refreshTokenTimeToLive(Duration.ofHours(2))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(gatewayClient);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        JWKSet jwkSet = new JWKSet(keyManager.generateRsaKey());
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public ProviderSettings providerSettings() {
        return ProviderSettings.builder()
                .issuer("http://localhost:8082")
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
                Authentication principal = context.getPrincipal();
                Set<String> authorities = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                context.getClaims().claim("scope", authorities);
            }
        };
    }

    /*@Bean
   public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
       JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
       RegisteredClient gatewayClient = registeredClientRepository.findByClientId("gateway");

       if (gatewayClient == null) {
           return getRegisteredClientRepository(jdbcTemplate);
       }

       return new InMemoryRegisteredClientRepository(gatewayClient);
   }
    private JdbcRegisteredClientRepository getRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        RegisteredClient gatewayClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("gateway")
                .clientSecret(passwordEncoder.encode("secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8081/login/oauth2/code/gateway-client-oidc")
                .redirectUri("http://127.0.0.1:8081/authorized")
                .scope(OidcScopes.OPENID)
                .scope("auth.user")
                .build();

        JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
        registeredClientRepository.save(gatewayClient);
        return registeredClientRepository;
    }*/
}