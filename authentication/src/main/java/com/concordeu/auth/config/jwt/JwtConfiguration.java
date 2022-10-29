package com.concordeu.auth.config.jwt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
@NoArgsConstructor
@ConfigurationProperties(prefix = "application.jwt")
@Setter
@Getter
public class JwtConfiguration {
    private String secretKey;
    private String tokenPrefix;
    private String tokenExpirationAfterDays;

    public String getAuthorizationHeader() {
        return AUTHORIZATION;
    }
}
