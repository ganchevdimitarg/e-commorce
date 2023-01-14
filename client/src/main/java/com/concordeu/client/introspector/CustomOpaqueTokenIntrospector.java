package com.concordeu.client.introspector;

import com.concordeu.client.principal.FacebookOAuth2AuthPrincipal;
import com.concordeu.client.principal.GitHubOAuth2AuthPrincipal;
import com.concordeu.client.principal.GoogleOAuth2AuthPrincipal;
import com.concordeu.client.principal.OAuth2AuthPrincipal;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token.startsWith(GOOGLE_PREFIX)) {
            return new GoogleOAuth2AuthPrincipal().getPrincipal(token);
        }
        if (token.startsWith(GITHUB_PREFIX)) {
            return new GitHubOAuth2AuthPrincipal().getPrincipal(token);
        }
        return new FacebookOAuth2AuthPrincipal().getPrincipal(token);
    }
}
