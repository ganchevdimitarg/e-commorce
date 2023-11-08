package com.concordeu.profile.config;

import com.concordeu.client.introspector.CustomOpaqueTokenIntrospector;
import com.concordeu.profile.handler.CustomLogoutHandler;
import io.netty.util.internal.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import static com.concordeu.profile.handler.CustomLogoutHandler.TOKEN_PREFIX;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    private final CustomLogoutHandler logoutHandler;
    private final JwtDecoder jwtDecoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/v3/api-docs**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/profile/register-worker").permitAll()
                        .requestMatchers("/api/v1/profile/register-user").permitAll()
                        .requestMatchers("/api/v1/profile/password-reset").permitAll()
                        .anyRequest().authenticated()
                )
                .logout((logout) -> logout
                        .logoutUrl("/api/v1/profile/logout")
                        .addLogoutHandler(this.logoutHandler)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
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
    AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver() {
        AuthenticationManager jwt = new ProviderManager(new JwtAuthenticationProvider(this.jwtDecoder));
        AuthenticationManager opaqueToken = new ProviderManager(
                new OpaqueTokenAuthenticationProvider(opaqueTokenIntrospector()));
        return (request) -> isJwt(request, this.jwtDecoder) ? jwt : opaqueToken;
    }

    private boolean isJwt(HttpServletRequest request, JwtDecoder jwtDecoder) {
        try {
            jwtDecoder.decode(request
                    .getHeader(HttpHeaders.AUTHORIZATION)
                    .replace(TOKEN_PREFIX, StringUtil.EMPTY_STRING)
            );
            return true;
        } catch (BadJwtException e) {
            return false;
        }
    }
}