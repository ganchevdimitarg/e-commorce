package com.ganchevdimitarg.catalog.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the retention purge: binds {@link RetentionProperties} and supplies the clock the
 * scheduler uses to compute the cutoff.
 */
@Configuration
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionConfig {

    /**
     * UTC so the retention window does not shift with the host's time zone.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
