package com.concordeu.profile.handler;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomLogoutHandler implements LogoutHandler {
    private static final String GOOGLE_PREFIX = "ya29.";
    private static final String GITHUB_PREFIX = "gho_";
    private final JwtDecoder jwtDecoder;
    private final WebClient webClient;
    private final Gson mapper;

    @Value("${github.clientId}")
    private String gitGithubClientId;
    @Value("${github.secret}")
    private String gitGithubSecret;
    @Value("${ecommerce.oauth2.clientId}")
    private String ecommerceOAuth2ClientId;
    @Value("${ecommerce.oauth2.secret}")
    private String ecommerceOAuth2Secret;


    @Override
    public void logout(HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse,
                       Authentication authentication) {
        String token = httpServletRequest.getHeader("Authorization").replace("Bearer ", "");

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

    //TODO method does not work. Should return 204 No Content, but returns 404 Not Found from DELETE/////
    private void revokeGitHubAccessToken(String token) {
        String requestBody = mapper.toJson(String.format("""
                {
                    "access_token":"%s"
                }
                """, token));

        ResponseEntity<Void> response = webClient
                .method(HttpMethod.DELETE)
                .uri("https://api.github.com/applications/309e3867b9f10d4a8270/grant")
                .header("Accept", "application/vnd.github+json")
                .headers(headers -> headers.setBasicAuth(gitGithubClientId, gitGithubSecret))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(Void.class)
                .block();

        log.info("""
                User login with GITHUB successful logout.
                Token: {}
                Status: {}
                """, token, response.getStatusCodeValue());

    }

    private void revokeFacebookAccessToken(String token) {
        String revoke = "https://graph.facebook.com/v15.0/me/permissions?access_token=" + token;
        ResponseEntity<Void> response = webClient
                .delete()
                .uri(revoke)
                .header("Authorization", token)
                .retrieve()
                .toEntity(Void.class)
                .block();

        log.info("""
                User login with FACEBOOK successful logout.
                Token: {}
                Status: {}
                """, token, response.getStatusCodeValue());
    }

    private void revokeGoogleAccessToken(String token) {
        String revoke = "https://oauth2.googleapis.com/revoke?token=" + token;
        ResponseEntity<Void> revokeResponse = revokeTokenPost(token, revoke);

        log.info("""
                User login with GOOGLE AUTH SERVER successful logout.
                Token: {}
                Status: {}
                """, token, revokeResponse.getStatusCodeValue());
    }


    private void revokeECommerceAccessToken(String token) {
        ResponseEntity<Void> revokeResponse = webClient
                .post()
                .uri("http://localhost:8082/oauth2/revoke?token=" + token)
                .headers(headers -> headers.setBasicAuth(ecommerceOAuth2ClientId, ecommerceOAuth2Secret))
                .retrieve()
                .toEntity(Void.class)
                .block();
        log.info("""
                User login with E-COMMERCE AUTH SERVER successful logout.
                Token: {}
                Status: {}
                """, token, revokeResponse.getStatusCode().value());
    }

    private ResponseEntity<Void> revokeTokenPost(String token, String revoke) {
        return webClient
                .post()
                .uri(revoke)
                .header("Authorization", token)
                .retrieve()
                .toEntity(Void.class)
                .block();
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