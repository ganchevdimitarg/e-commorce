package com.concordeu.client.principal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static com.concordeu.client.security.UserRole.USER;

@Component
@Slf4j
public class GitHubOAuth2AuthPrincipal {
    private static final String OAUTH2_GITHUB_APIS_USER_INFO_URI = "https://api.github.com/user";
    private static final String OAUTH2_GITHUB_APIS_USER_EMAIL_URI = "https://api.github.com/user/emails";
    private final RestTemplate restTemplate = new RestTemplate();

    public OAuth2IntrospectionAuthenticatedPrincipal getPrincipal(String token) {
        RequestEntity<?> userEmail = buildUserEmailRequest(token);
        RequestEntity<?> userInfo = buildUserInfoRequest(token);
        try {
            ResponseEntity<Map<String, Object>> getUserInfo = this.restTemplate.exchange(userInfo, new ParameterizedTypeReference<>() {
            });
            ResponseEntity<List<Map<String, Object>>> getUserEmail = this.restTemplate.exchange(userEmail, new ParameterizedTypeReference<>() {
            });
            String username = String.valueOf(Objects.requireNonNull(getUserEmail.getBody()).get(0).get("email"));

            Map<String, Object> attributes = getUserInfo.getBody();

            Collection<GrantedAuthority> authorities = USER.getGrantedAuthorities()
                    .stream()
                    .map(a -> new SimpleGrantedAuthority("SCOPE_" + a))
                    .collect(Collectors.toSet());

            assert attributes != null;
            OAuth2IntrospectionAuthenticatedPrincipal principal = new OAuth2IntrospectionAuthenticatedPrincipal(username, attributes, authorities);
            log.info("User {} is login with GitHub", username);
            return principal;

        } catch (Exception ex) {
            log.info(ex.getMessage());
            throw new BadOpaqueTokenException(ex.getMessage(), ex);
        }
    }

    private RequestEntity<?> buildUserInfoRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);

        return new RequestEntity<>(headers, HttpMethod.GET, URI.create(OAUTH2_GITHUB_APIS_USER_INFO_URI));
    }

    private RequestEntity<?> buildUserEmailRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.add("Authorization", "Bearer " + token);

        return new RequestEntity<>(headers, HttpMethod.GET, URI.create(OAUTH2_GITHUB_APIS_USER_EMAIL_URI));
    }
}
