package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.cache.LoggingCacheErrorHandler;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
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
                // v4: the value serializer is typed to ProductResponseDto and writes no type
                // metadata; a new prefix forces a miss on v3 entries, which contain @class.
                .prefixCacheNameWith("catalog:v4:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(ProductResponseDto.class)));

        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
                writer -> writer.immediateWrites());

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(config)
                .build();
    }
}
