package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the schema validator as a bean. The class itself is not a component because its
 * fixture constructor would otherwise force an {@code @Autowired} choice between
 * constructors.
 */
@Configuration
public class EventSchemaConfig {

    @Bean
    EventSchemaValidator eventSchemaValidator(ObjectMapper objectMapper) {
        return new EventSchemaValidator(objectMapper);
    }
}
