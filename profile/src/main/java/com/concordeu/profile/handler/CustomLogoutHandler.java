package com.concordeu.profile.handler;

import com.concordeu.profile.dto.GithubRevokeTokenDto;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class CustomLogoutHandler implements LogoutHandler {
    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";
    private final JwtDecoder jwtDecoder;

    private final WebClient webClient;
    private final Gson mapper;

    @Value("${github.clientId}")
    private String githubClientId;
    @Value("${github.secret}")
    private String githubSecret;
    @Value("${github.revoke.uri}")
    private String githubRevokeUri;
    @Value("${ecommerce.oauth2.clientId}")
    private String ecommerceOAuth2ClientId;
    @Value("${ecommerce.oauth2.secret}")
    private String ecommerceOAuth2Secret;
    @Value("${ecommerce.revoke.uri}")
    private String ecommerceRevokeUri;
    @Value("${google.revoke.uri}")
    private String googleRevokeUri;
    @Value("${facebook.revoke.uri}")
    private String facebookRevokeUri;

    public CustomLogoutHandler(JwtDecoder jwtDecoder,
                               @Qualifier("logout") WebClient webClient,
                               Gson mapper) {
        this.jwtDecoder = jwtDecoder;
        this.webClient = webClient;
        this.mapper = mapper;
    }

    @Override
    public void logout(HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse,
                       Authentication authentication) {
        String token = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION).replace("Bearer ", "");

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

        ResponseEntity<Void> response = webClient
                .method(HttpMethod.DELETE)
                .uri(githubRevokeUri)
                .headers(headers -> headers.setBasicAuth(githubClientId, githubSecret))
                .accept(MediaType.valueOf("application/vnd.github+json"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("""
                User login with GITHUB successful logout.
                Token: {}
                Status: {}
                """, token, response.getStatusCodeValue());
    }

    private void revokeFacebookAccessToken(String token) {
        ResponseEntity<Void> response = webClient
                .delete()
                .uri(facebookRevokeUri + token)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("""
                User login with FACEBOOK successful logout.
                Token: {}
                Status: {}
                """, token, response.getStatusCodeValue());
    }

    private void revokeGoogleAccessToken(String token) {
        ResponseEntity<Void> revokeResponse = webClient
                .post()
                .uri(googleRevokeUri + token)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("""
                User login with GOOGLE AUTH SERVER successful logout.
                Token: {}
                Status: {}
                """, token, revokeResponse.getStatusCodeValue());
    }


    private void revokeECommerceAccessToken(String token) {
        ResponseEntity<Void> revokeResponse = webClient
                .post()
                .uri(ecommerceRevokeUri + token)
                .headers(headers -> headers.setBasicAuth(ecommerceOAuth2ClientId, ecommerceOAuth2Secret))
                .retrieve()
                .toBodilessEntity()
                .block();
        log.info("""
                User login with E-COMMERCE AUTH SERVER successful logout.
                Token: {}
                Status: {}
                """, token, revokeResponse.getStatusCode().value());
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