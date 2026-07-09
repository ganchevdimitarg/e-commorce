package com.ganchevdimitarg.notification.config;

public final class KafkaTopics {

    /**
     * Inbound email-request events, published by the order service — the name must match
     * order's {@code KafkaTopics.ORDER_NOTIFICATION_REQUESTED}. DLT is this name + ".DLT"
     * (Spring default suffix).
     */
    public static final String ORDER_NOTIFICATION_REQUESTED = "order.notification.requested";
    public static final String ORDER_NOTIFICATION_REQUESTED_DLT = "order.notification.requested.DLT";
    public static final String GROUP = "notification-group";

    private KafkaTopics() {
    }
}
