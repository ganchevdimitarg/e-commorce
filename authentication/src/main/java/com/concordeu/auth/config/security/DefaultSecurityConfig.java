package com.concordeu.auth.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@RequiredArgsConstructor
public class DefaultSecurityConfig {
    private final AuthenticationManagerBuilder authManagerBuilder;

    @Bean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilter(new AuthenticationFilter(authManagerBuilder.getOrBuild()))
                .authorizeRequests()
                .anyRequest()
                .authenticated()
                .and()
                .formLogin(withDefaults());
        return http.build();
    }

}
