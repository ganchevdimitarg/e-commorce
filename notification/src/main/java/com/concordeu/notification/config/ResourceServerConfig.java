package com.concordeu.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.mvcMatcher("/api/v1/notification/**")
                .authorizeRequests()
                .mvcMatchers("/api/v1/notification/**")
                .access("hasAuthority('SCOPE_auth.user')")
                .and()
                .oauth2ResourceServer()
                .jwt();
        return http.build();
    }
}