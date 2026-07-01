package com.ganchevdimitarg.auth.service;

/** Kafka topic names owned by the authentication service. */
public final class AuthTopics {

    public static final String USER_REGISTERED = "auth.user.registered";
    public static final String USER_DELETED = "auth.user.deleted";
    public static final String PASSWORD_RESET_REQUESTED = "auth.password.reset-requested";

    private AuthTopics() {
    }
}
