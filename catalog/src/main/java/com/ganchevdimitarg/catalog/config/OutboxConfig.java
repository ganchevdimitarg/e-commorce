package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.event.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the transactional-outbox relay: binds {@link OutboxProperties} and enables the
 * scheduled delivery loop.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {
}
