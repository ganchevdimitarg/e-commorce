package com.concordeu.auth.config.security;

import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JwtSecretKey {

    private final JwtConfiguration jwtConfiguration;

    @Bean
    public Algorithm secretKey() {
        return Algorithm.HMAC256(jwtConfiguration.getSecretKey().getBytes());
    }

}
