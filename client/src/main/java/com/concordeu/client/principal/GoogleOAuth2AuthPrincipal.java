package com.concordeu.client.principal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.concordeu.client.security.UserRole.USER;

@Component
@Slf4j
public class GoogleOAuth2AuthPrincipal {
    private static final String OAUTH2_GOOGLE_APIS_USER_INFO_URI = "https://oauth2.googleapis.com/tokeninfo";
    private final RestTemplate restTemplate = new RestTemplate();

    public OAuth2AuthenticatedPrincipal getPrincipal(String token) {
        RequestEntity<?> requestEntity = buildRequest(token);
        try {
            ResponseEntity<Map<String, Object>> responseEntity = this.restTemplate.exchange(requestEntity, new ParameterizedTypeReference<>() {
            });
            String username = String.valueOf(Objects.requireNonNull(responseEntity.getBody()).get("email"));

            Map<String, Object> attributes = responseEntity.getBody();
            attributes.put("exp", Instant.ofEpochSecond(Long.parseLong(attributes.get("exp").toString())));

            Collection<GrantedAuthority> authorities = USER.getGrantedAuthorities()
                    .stream()
                    .map(a -> new SimpleGrantedAuthority("SCOPE_" + a))
                    .collect(Collectors.toSet());

            OAuth2IntrospectionAuthenticatedPrincipal principal = new OAuth2IntrospectionAuthenticatedPrincipal(username, attributes, authorities);
            log.info("User {} is login with Google", username);
            return principal;

        } catch (Exception ex) {
            log.debug(ex.getMessage());
            throw new BadOpaqueTokenException(ex.getMessage(), ex);
        }
    }

    private RequestEntity<?> buildRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("access_token", token);

        return new RequestEntity<>(body, headers, HttpMethod.POST, URI.create(OAUTH2_GOOGLE_APIS_USER_INFO_URI));

    }
}
