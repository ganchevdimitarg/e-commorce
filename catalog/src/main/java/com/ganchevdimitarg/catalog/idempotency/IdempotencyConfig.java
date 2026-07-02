package com.ganchevdimitarg.catalog.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class IdempotencyConfig implements WebMvcConfigurer {

    private final StringRedisTemplate redis;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(redis))
                .addPathPatterns("/api/v1/catalog/**");
    }
}
