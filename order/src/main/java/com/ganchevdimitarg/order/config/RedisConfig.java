package com.ganchevdimitarg.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.util.RedisLockRegistry;

@Configuration
public class RedisConfig {

    // [convention: "idempotency:order-lock" rather than "idempotency:order" — RedisLockRegistry
    // builds its own Redis keys as `registryKey + ':' + path`, which with a bare "idempotency:order"
    // registry key is byte-identical to IdempotencyService's cache key ("idempotency:order:" + key).
    // That collision let the lock's raw (non-JSON) owner-id value overwrite the cached response,
    // breaking deserialization (reproduced via IdempotencyServiceIT). The "-lock" suffix keeps the
    // two keyspaces disjoint for every possible idempotency key.]
    private static final String LOCK_REGISTRY_KEY = "idempotency:order-lock";
    private static final long LOCK_EXPIRY_MILLIS = 30_000L;

    // [convention: no-arg GenericJackson2JsonRedisSerializer rather than the ObjectMapper-injecting
    // overload — Boot 4's auto-configured ObjectMapper bean is Jackson 3 and is not the Jackson 2
    // com.fasterxml.jackson.databind.ObjectMapper this serializer needs; wiring it in produced a
    // SerializationException on read (verified via IdempotencyServiceIT). The no-arg constructor
    // builds its own internal Jackson 2 mapper and round-trips correctly.]
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, LOCK_REGISTRY_KEY, LOCK_EXPIRY_MILLIS);
    }
}
