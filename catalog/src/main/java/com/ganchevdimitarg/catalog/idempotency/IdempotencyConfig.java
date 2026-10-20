package com.ganchevdimitarg.catalog.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class IdempotencyConfig implements WebMvcConfigurer {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Bean
    public IdempotencyStore idempotencyStore() {
        return new IdempotencyStore(redis, objectMapper);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Calling the @Bean method through the CGLIB proxy returns the singleton; injecting the
        // store into this configuration class instead would create a configuration cycle.
        registry.addInterceptor(new IdempotencyInterceptor(idempotencyStore()))
                .addPathPatterns("/api/v1/catalog/**");
    }
}
