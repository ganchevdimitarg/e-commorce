package com.concordeu.profile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtDecoderConfig {
    private final String jwkIssuerUri;

    public JwtDecoderConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String jwkIssuerUri) {
        this.jwkIssuerUri = jwkIssuerUri;
    }

    @Bean
    JwtDecoder jwtDecoderCustom() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(this.jwkIssuerUri);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(this.jwkIssuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer);
        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }
}
