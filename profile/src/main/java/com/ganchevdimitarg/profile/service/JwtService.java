package com.ganchevdimitarg.profile.service;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

public interface JwtService {
    Mono<Void> saveToken(String token, String username);
    <T> T extractClaim(String token, Function<Claims, T> claimsResolver);
    Mono<String> generateToken(UserDetails userDetails);
    Mono<String> generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails);
    Mono<Boolean> isTokenValid(String token);
}
