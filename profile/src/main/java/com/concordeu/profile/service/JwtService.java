package com.concordeu.profile.service;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.function.Function;

public interface JwtService {
    void saveToken(String token, String username);
    String extractUsername(String token);
    <T> T extractClaim(String token,
                       Function<Claims, T> claimsResolver);
    String generateToken(UserDetails userDetails);
    String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails);
    boolean isTokenValid(String token);
}
