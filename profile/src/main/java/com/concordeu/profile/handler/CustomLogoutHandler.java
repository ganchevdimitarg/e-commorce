package com.concordeu.profile.handler;

import com.concordeu.profile.dto.GithubRevokeTokenDto;
import com.google.gson.Gson;
import io.micrometer.observation.annotation.Observed;
import io.netty.util.internal.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@Slf4j
public class CustomLogoutHandler implements LogoutHandler {
    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";
    public static final String TOKEN_PREFIX = "Bearer ";
    private final WebClient webClient;
    private final JwtDecoder jwtDecoder;
    private final Gson mapper;
    private final String githubClientId;
    private final String githubSecret;
    private final String githubRevokeUri;
    private final String ecommerceOAuth2ClientId;
    private final String ecommerceOAuth2Secret;
    private final String ecommerceRevokeUri;
    private final String googleRevokeUri;
    private final String facebookRevokeUri;

    public CustomLogoutHandler(WebClient.Builder webClientBuilder,
                               JwtDecoder jwtDecoder,
                               Gson mapper,
                               @Value("${github.clientId}") String githubClientId,
                               @Value("${github.secret}") String githubSecret,
                               @Value("${github.revoke.uri}") String githubRevokeUri,
                               @Value("${ecommerce.oauth2.clientId}") String ecommerceOAuth2ClientId,
                               @Value("${ecommerce.oauth2.secret}") String ecommerceOAuth2Secret,
                               @Value("${ecommerce.revoke.uri}") String ecommerceRevokeUri,
                               @Value("${google.revoke.uri}") String googleRevokeUri,
                               @Value("${facebook.revoke.uri}") String facebookRevokeUri) {
        this.webClient = webClientBuilder.build();
        this.jwtDecoder = jwtDecoder;
        this.mapper = mapper;
        this.githubClientId = githubClientId;
        this.githubSecret = githubSecret;
        this.githubRevokeUri = githubRevokeUri;
        this.ecommerceOAuth2ClientId = ecommerceOAuth2ClientId;
        this.ecommerceOAuth2Secret = ecommerceOAuth2Secret;
        this.ecommerceRevokeUri = ecommerceRevokeUri;
        this.googleRevokeUri = googleRevokeUri;
        this.facebookRevokeUri = facebookRevokeUri;
    }

    @Override
    @Observed(
            name = "user.name",
            contextualName = "logout",
            lowCardinalityKeyValues = {"method", "logout"}
    )
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String token = request.getHeader(HttpHeaders.AUTHORIZATION)
                .replace(TOKEN_PREFIX, StringUtil.EMPTY_STRING);

        if (isJwt(token)) {
            revokeECommerceAccessToken(token);
        } else if (token.startsWith(GOOGLE_PREFIX)) {
            revokeGoogleAccessToken(token);
        } else if (token.startsWith(GITHUB_PREFIX)) {
            revokeGitHubAccessToken(token);
        } else {
            revokeFacebookAccessToken(token);
        }
    }

    private void revokeGitHubAccessToken(String token) {
        String requestBody = mapper.toJson(
                GithubRevokeTokenDto.builder()
                        .accessToken(token)
                        .build()
        );

        webClient
                .method(HttpMethod.DELETE)
                .uri(githubRevokeUri)
                .headers(headers -> headers.setBasicAuth(githubClientId, githubSecret))
                .accept(MediaType.valueOf("application/vnd.github+json"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(d -> log.info("""
                        User login with GITHUB successful logout.
                        Token: {}
                        Status: {}
                        """, token, d.getStatusCode().value()))
                .doOnError(d -> log.warn("""
                        There is an error with logging out of the user's GITHUB account.
                        Token: {}
                        Status: {}
                        """, token, d.getMessage()))
                .subscribe();
    }

    private void revokeFacebookAccessToken(String token) {
        webClient
                .delete()
                .uri(facebookRevokeUri + token)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(d -> log.info("""
                        User login with FACEBOOK successful logout.
                        Token: {}
                        Status: {}
                        """, token, d.getStatusCode().value()))
                .doOnError(d -> log.warn("""
                        There is an error with logging out of the user's Facebook account.
                        Token: {}
                        Status: {}
                        """, token, d.getMessage()))
                .subscribe();
    }

    private void revokeGoogleAccessToken(String token) {
        webClient
                .post()
                .uri(googleRevokeUri + token)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(d -> log.info("""
                        User login with GOOGLE AUTH SERVER successful logout.
                        Token: {}
                        Status: {}
                        """, token, d.getStatusCode().value()))
                .doOnError(d -> log.warn("""
                        There is an error with logging out of the user's GOOGLE AUTH SERVER account.
                        Token: {}
                        Status: {}
                        """, token, d.getMessage()))
                .subscribe();
    }


    private void revokeECommerceAccessToken(String token) {
        webClient
                .post()
                .uri(ecommerceRevokeUri + token)
                .headers(headers -> headers.setBasicAuth(ecommerceOAuth2ClientId, ecommerceOAuth2Secret))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(d -> log.info("""
                        User login with E-COMMERCE AUTH SERVER successful logout.
                        Token: {}
                        Status: {}
                        """, token, d.getStatusCode().value()))
                .doOnError(d -> log.warn("""
                        There is an error with logging out of the user's E-COMMERCE AUTH SERVER account.
                        Token: {}
                        Status: {}
                        """, token, d.getMessage()))
                .subscribe();
    }


    private boolean isJwt(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (BadJwtException e) {
            return false;
        }
    }
}