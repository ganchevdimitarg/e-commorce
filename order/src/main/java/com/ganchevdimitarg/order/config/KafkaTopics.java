package com.ganchevdimitarg.order.config;

/**
 * Kafka topic names, following the {@code <domain>.<entity>.<event>} convention. The
 * notification service consumes {@code ORDER_NOTIFICATION_REQUESTED}; the two must be
 * changed in lockstep.
 */
public final class KafkaTopics {
    public static final String ORDER_NOTIFICATION_REQUESTED = "order.notification.requested";

    private KafkaTopics() {
    }
}
