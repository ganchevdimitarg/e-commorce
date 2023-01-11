package com.concordeu.client.introspector;

import com.concordeu.client.principal.FacebookOAuth2AuthPrincipal;
import com.concordeu.client.principal.GitHubOAuth2AuthPrincipal;
import com.concordeu.client.principal.GoogleOAuth2AuthPrincipal;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class CustomOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";
    private GoogleOAuth2AuthPrincipal googleOAuth2AuthPrincipal;
    private GitHubOAuth2AuthPrincipal gitHubOAuth2AuthPrincipal;
    private FacebookOAuth2AuthPrincipal facebookOAuth2AuthPrincipal;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token.startsWith(GOOGLE_PREFIX)) {
            return googleOAuth2AuthPrincipal.getPrincipal(token);
        }
        if (token.startsWith(GITHUB_PREFIX)) {
            return gitHubOAuth2AuthPrincipal.getPrincipal(token);
        }
        return facebookOAuth2AuthPrincipal.getPrincipal(token);
    }
}
