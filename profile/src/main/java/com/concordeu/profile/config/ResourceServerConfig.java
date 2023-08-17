package com.concordeu.profile.config;

import com.concordeu.client.introspector.CustomOpaqueTokenIntrospector;
import com.concordeu.profile.handler.CustomLogoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerAdapter;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    private final CustomLogoutHandler logoutHandler;
    private final JwtDecoder jwtDecoder;

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) throws Exception {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange((auth) -> auth
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/api/v1/profile/register-admin").permitAll()
                        .pathMatchers("/api/v1/profile/register-worker").permitAll()
                        .pathMatchers("/api/v1/profile/register-user").permitAll()
                        .pathMatchers("/api/v1/profile/password-reset").permitAll()
                        .anyExchange().authenticated()
                )
                .logout((logout) -> logout
                                .logoutUrl("/api/v1/profile/logout")
                                .logoutHandler(logoutHandler)
                                .logoutSuccessHandler(new HttpStatusReturningServerLogoutSuccessHandler(HttpStatus.OK))
                )
                .oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
                        .authenticationManagerResolver(this.tokenAuthenticationManagerResolver())
                );

        return http.build();
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        return new CustomOpaqueTokenIntrospector();
    }

    @Bean
    ReactiveAuthenticationManagerResolver<ServerWebExchange> tokenAuthenticationManagerResolver() {
        Mono<ReactiveAuthenticationManager> jwt = Mono.just(
                new ReactiveAuthenticationManagerAdapter(
                        new ProviderManager(new JwtAuthenticationProvider(jwtDecoder))
                )
        );
        Mono<ReactiveAuthenticationManager> opaqueToken = Mono.just(
                new ReactiveAuthenticationManagerAdapter(
                        new ProviderManager(new OpaqueTokenAuthenticationProvider(opaqueTokenIntrospector()))
                )
        );
        return context -> this.isJwt(context.getAttribute(HttpHeaders.AUTHORIZATION).toString().replace("Bearer ", ""), jwtDecoder) ? jwt : opaqueToken;
    }

    private boolean isJwt(String token, JwtDecoder jwtDecoder) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (BadJwtException e) {
            log.debug(e.getMessage());
            return false;
        }
    }
}