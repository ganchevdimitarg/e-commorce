package com.ganchevdimitarg.order.config;

/**
 * Kafka topic names. NOTE: {@code SENT_MAIL} retains the physical name "sentMail" because the
 * notification service consumes it; renaming to the convention {@code order.notification.requested}
 * requires a coordinated cross-service change and is tracked separately.
 */
public final class KafkaTopics {
    public static final String SENT_MAIL = "sentMail";

    private KafkaTopics() {
    }
}
