package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.cache.LoggingCacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                // v3: ProductResponseDto gained avgStars/ratingCount; a new prefix forces a
                // miss on pre-rating entries instead of serving zero ratings until the TTL.
                // (v2 introduced the version component that backs ETags.)
                .prefixCacheNameWith("catalog:v3:")
                // Uses @class type info — renaming DTOs invalidates cache entries
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
                writer -> writer.immediateWrites());

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(config)
                .build();
    }
}
