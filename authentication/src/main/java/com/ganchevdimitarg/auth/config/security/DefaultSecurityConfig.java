package com.ganchevdimitarg.auth.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class DefaultSecurityConfig {

    @Bean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
        // Configures authentication and authorization rules; customizes form login
        http
                .csrf(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().authenticated());
        return http.build();
    }

}
