package com.ganchevdimitarg.profile.config;

//import com.ganchevdimitarg.client.introspector.CustomOpaqueTokenIntrospector;

import com.ganchevdimitarg.profile.handler.CustomLogoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMethodSecurity
@Slf4j
public class ResourceServerConfig {

    private final CustomLogoutHandler logoutHandler;
    private final String jwkSetUri;

    public ResourceServerConfig(
            CustomLogoutHandler logoutHandler,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        this.logoutHandler = logoutHandler;
        this.jwkSetUri = jwkSetUri;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

//    @Bean
//    public ReactiveOpaqueTokenIntrospector opaqueTokenIntrospector() {
//        return new CustomOpaqueTokenIntrospector();
//    }

    @Bean
    public ReactiveAuthenticationManagerResolver<ServerWebExchange> tokenAuthenticationManagerResolver() {
        ReactiveAuthenticationManager jwtManager =
                new JwtReactiveAuthenticationManager(jwtDecoder());
//        ReactiveAuthenticationManager opaqueTokenManager =
//                new OpaqueTokenReactiveAuthenticationManager(opaqueTokenIntrospector());

        return exchange -> Mono.just(jwtManager);
//        return exchange -> isJwt(exchange) ? Mono.just(jwtManager) : Mono.just(opaqueTokenManager);
    }

    // ServerHttpSecurity received as method parameter - NOT a constructor field
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Disables CSRF; configures authorization and logout
        return http
                // Configures authorization rules for specified paths
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        .pathMatchers(
                                "/api/v1/profile/register-admin",
                                "/api/v1/profile/register-worker",
                                "/api/v1/profile/register-user",
                                "/api/v1/profile/password-reset"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/profile/logout")
                        .logoutHandler(logoutHandler)
                        .logoutSuccessHandler(new HttpStatusReturningServerLogoutSuccessHandler())
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(tokenAuthenticationManagerResolver())
                )
                .build();
    }

    private boolean isJwt(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        String token = header.substring(7);
        return token.chars().filter(c -> c == '.').count() == 2;
    }
}