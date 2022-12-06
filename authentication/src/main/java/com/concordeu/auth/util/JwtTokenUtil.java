package com.concordeu.auth.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.concordeu.auth.config.jwt.JwtConfiguration;
import com.concordeu.auth.config.jwt.JwtSecretKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenUtil {
    private final JwtSecretKey jwtSecretKey;
    private final JwtConfiguration jwtConfiguration;

    public String generateJwtToken(HttpServletRequest request, User user, int expiresInWeeks) {
        return JWT.create()
                .withSubject(user.getUsername())
                .withIssuer(request.getRequestURI().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(java.sql.Date.valueOf(LocalDate.now().plusWeeks(expiresInWeeks)))
                .withClaim("roles", user.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .sign(jwtSecretKey.secretKey());
    }

    public void setErrorHeader(HttpServletResponse response, JWTVerificationException ex) throws IOException {
        log.error("Error logging in: {}", ex.getMessage());
        response.setHeader("error", ex.getMessage());
        response.setStatus(FORBIDDEN.value());
        Map<String, String> error = new HashMap<>();
        error.put("error_message", ex.getMessage());
        response.setContentType(APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(), error);
    }

    public void setResponseWithJwts(HttpServletResponse response, String access_token, String refresh_token) throws IOException {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", jwtConfiguration.getTokenPrefix() + access_token);
        tokens.put("refresh_token", jwtConfiguration.getTokenPrefix() + refresh_token);
        response.setContentType(APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(), tokens);
    }
}
