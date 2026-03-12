package com.ganchevdimitarg.profile.handler;

import com.ganchevdimitarg.profile.property.EcommerceOAuth2Properties;
import com.ganchevdimitarg.profile.property.GithubProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomLogoutHandler implements ServerLogoutHandler {

    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";

    private final WebClient webClient;
    private final GithubProperties githubProperties;
    private final EcommerceOAuth2Properties ecommerceProperties;

    @Value("${auth.server.revoke-uri}")
    private String ecommerceRevokeUri;

    /**
     * Handles logout by extracting the Bearer token from the request Authorization header
     * and delegating to the appropriate revocation strategy based on token type.
     * Supports JWT (e-commerce), Google, GitHub, and Facebook tokens.
     *
     * @param exchange        the current web filter exchange containing the request headers
     * @param authentication  the current authentication (not used directly — token extracted from header)
     * @return a {@link Mono} completing empty after revocation attempt, never errors
     */
    @Override
    @NullMarked
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        String authHeader = exchange.getExchange().getRequest()
                .getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Logout called with missing or invalid Authorization header");
            return Mono.empty();
        }

        String token = authHeader.substring(7);

        if (isJwt(token)) {
            return revokeECommerceAccessToken(token);
        } else if (token.startsWith(GOOGLE_PREFIX)) {
            return revokeGoogleAccessToken(token);
        } else if (token.startsWith(GITHUB_PREFIX)) {
            return revokeGitHubAccessToken(token);
        } else {
            return revokeFacebookAccessToken(token);
        }
    }

    private Mono<Void> revokeGitHubAccessToken(String token) {
        record AccessTokenBody(String access_token) {}

        return webClient
                .method(HttpMethod.DELETE)
                .uri("https://api.github.com/applications/{clientId}/grant",
                        githubProperties.clientId())
                .header("Accept", "application/vnd.github+json")
                .headers(headers -> headers.setBasicAuth(
                        githubProperties.clientId(),
                        githubProperties.secret()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AccessTokenBody(token))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info(
                        "GitHub logout successful. Status: {}", response.getStatusCode()))
                .onErrorResume(e -> {
                    log.error("GitHub token revocation failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> revokeFacebookAccessToken(String token) {
        return webClient
                .delete()
                .uri("https://graph.facebook.com/v15.0/me/permissions")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info(
                        "Facebook logout successful. Status: {}", response.getStatusCode()))
                .onErrorResume(e -> {
                    log.error("Facebook token revocation failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> revokeGoogleAccessToken(String token) {
        return webClient
                .post()
                .uri("https://oauth2.googleapis.com/revoke")
                .bodyValue(Map.of("token", token))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info(
                        "Google logout successful. Status: {}", response.getStatusCode()))
                .onErrorResume(e -> {
                    log.error("Google token revocation failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> revokeECommerceAccessToken(String token) {
        return webClient
                .post()
                .uri(ecommerceRevokeUri)
                .headers(headers -> headers.setBasicAuth(
                        ecommerceProperties.clientId(),
                        ecommerceProperties.secret()))
                .bodyValue(Map.of("token", token))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info(
                        "E-Commerce logout successful. Status: {}", response.getStatusCode()))
                .onErrorResume(e -> {
                    log.error("E-Commerce token revocation failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private boolean isJwt(String token) {
        return token.chars().filter(c -> c == '.').count() == 2;
    }
}