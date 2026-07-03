package com.ganchevdimitarg.order.config;

import com.ganchevdimitarg.client.introspector.CustomOpaqueTokenIntrospector;
import com.ganchevdimitarg.order.excaption.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.order.excaption.ProblemAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    private final JwtDecoder jwtDecoder;
    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(this.tokenAuthenticationManagerResolver()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        return new CustomOpaqueTokenIntrospector();
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver() {
        AuthenticationManager jwt = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
        AuthenticationManager opaqueToken = new ProviderManager(
                new OpaqueTokenAuthenticationProvider(opaqueTokenIntrospector()));
        return (request) -> isJwt(request) ? jwt : opaqueToken;
    }

    private boolean isJwt(HttpServletRequest request) {
        try {
            jwtDecoder.decode(request
                    .getHeader("Authorization")
                    .replace("Bearer ", "")
            );
            return true;
        } catch (BadJwtException e) {
            log.debug(e.getMessage());
            return false;
        }
    }
}