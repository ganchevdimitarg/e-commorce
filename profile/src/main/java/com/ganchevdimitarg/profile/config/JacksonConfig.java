package com.ganchevdimitarg.profile.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson configuration.
 *
 * <p>Disables {@code FAIL_ON_UNKNOWN_PROPERTIES} so inbound events remain
 * tolerant consumers — the auth side may add fields to a JSON payload without
 * breaking deserialisation here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer tolerantDeserialization() {
        return builder -> builder.featuresToDisable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
