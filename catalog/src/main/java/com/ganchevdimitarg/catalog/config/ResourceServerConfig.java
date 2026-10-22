package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class ResourceServerConfig {
    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Boot applies this single chain to the separate management server too,
                        // so the management port is opened explicitly. It serves actuator data
                        // for monitoring scrapers and relies on the deployment network policy;
                        // the app port keeps the rules below and rejects info and prometheus.
                        .requestMatchers(this::isManagementRequest).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtAuthorityConverter.create()))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    private boolean isManagementRequest(HttpServletRequest request) {
        Integer managementPort = this.environment.getProperty("local.management.port", Integer.class);
        if (managementPort == null) {
            managementPort = this.environment.getProperty("management.server.port", Integer.class);
        }
        return managementPort != null && managementPort > 0 && request.getLocalPort() == managementPort;
    }
}
