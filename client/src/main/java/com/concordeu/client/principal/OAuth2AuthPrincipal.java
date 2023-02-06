package com.concordeu.client.principal;

import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

public interface OAuth2AuthPrincipal {
    OAuth2AuthenticatedPrincipal getPrincipal(String token);
}
