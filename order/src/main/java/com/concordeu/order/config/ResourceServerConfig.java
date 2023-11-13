package com.concordeu.order.config;

import com.concordeu.client.introspector.CustomOpaqueTokenIntrospector;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager=true)
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    public static final String TOKEN_PREFIX = "Bearer ";
    private final WebClient webClient;
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String jwkIssuerUri;

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange((auth) -> auth
                        .pathMatchers("/v3/api-docs**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
                        .authenticationManagerResolver(this.tokenAuthenticationManagerResolver())
                );

        return http.build();
    }

    //TODO add google, facebook, github
    @Bean
    public ReactiveOpaqueTokenIntrospector opaqueTokenIntrospector() {
        return new SpringReactiveOpaqueTokenIntrospector("http://127.0.0.1:8082/oauth2/introspect", webClient);
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(jwkIssuerUri);
    }

    @Bean
    ReactiveAuthenticationManagerResolver<ServerWebExchange> tokenAuthenticationManagerResolver() {
        ReactiveAuthenticationManager jwt = new JwtReactiveAuthenticationManager(jwtDecoder());
        ReactiveAuthenticationManager opaqueToken = new OpaqueTokenReactiveAuthenticationManager(opaqueTokenIntrospector());

        return context -> ResourceServerConfig.this.
                isJwt(
                        Objects.requireNonNull(
                                        context
                                                .getRequest()
                                                .getHeaders()
                                                .get(HttpHeaders.AUTHORIZATION)
                                )
                                .get(0)
                                .replace(TOKEN_PREFIX, StringUtil.EMPTY_STRING),
                        jwtDecoder()
                ) ? Mono.just(jwt) : Mono.just(opaqueToken);
    }

    private boolean isJwt(String token, ReactiveJwtDecoder jwtDecoder) {
        try {
            jwtDecoder.decode(token).subscribe();
            return true;
        } catch (BadJwtException e) {
            return false;
        }
    }
}