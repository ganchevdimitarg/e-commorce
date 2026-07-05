package com.ganchevdimitarg.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    /**
     * Buckets requests by the authenticated principal, falling back to the caller's
     * remote address for anonymous traffic so unauthenticated endpoints are still
     * throttled. Referenced from {@code RequestRateLimiter} as {@code #{@userKeyResolver}}.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.just(clientAddress(exchange.getRequest().getRemoteAddress())));
    }

    private String clientAddress(InetSocketAddress address) {
        return address != null ? address.getAddress().getHostAddress() : "anonymous";
    }
}
