package com.ganchevdimitarg.notification.config;

public final class KafkaTopics {

    /** Inbound email-request events. DLT is this name + ".DLT" (Spring default suffix). */
    public static final String NOTIFICATION_EMAIL_REQUESTED = "notification.email.requested";
    public static final String NOTIFICATION_EMAIL_REQUESTED_DLT = "notification.email.requested.DLT";
    public static final String GROUP = "notification-group";

    private KafkaTopics() {
    }
}
