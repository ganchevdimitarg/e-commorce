package com.ganchevdimitarg.payment.config;

import com.ganchevdimitarg.payment.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.payment.exception.ProblemAuthenticationEntryPoint;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {
    private final JwtDecoder jwtDecoder;
    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.introspection-uri}")
    private String introspectionUri;
    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-id}")
    private String introspectionClientId;
    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-secret}")
    private String introspectionClientSecret;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationManagerResolver<HttpServletRequest> resolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector(CircuitBreakerRegistry registry) {
        OpaqueTokenIntrospector delegate =
                new SpringOpaqueTokenIntrospector(introspectionUri, introspectionClientId, introspectionClientSecret);
        return new ResilientOpaqueTokenIntrospector(delegate, registry);
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> tokenAuthenticationManagerResolver(
            OpaqueTokenIntrospector introspector) {
        AuthenticationManager jwt = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
        AuthenticationManager opaqueToken = new ProviderManager(
                new OpaqueTokenAuthenticationProvider(introspector));
        return (request) -> isJwt(request) ? jwt : opaqueToken;
    }

    boolean isJwt(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : authorization.trim();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;   // opaque
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            return header.startsWith("{") && header.contains("alg");
        } catch (IllegalArgumentException e) {
            log.debug("Authorization token is not a JOSE header: {}", e.getMessage());
            return false;
        }
    }
}