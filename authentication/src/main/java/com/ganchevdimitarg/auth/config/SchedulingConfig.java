package com.ganchevdimitarg.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables Spring's `@Scheduled` support, required by the transactional-outbox relay. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
