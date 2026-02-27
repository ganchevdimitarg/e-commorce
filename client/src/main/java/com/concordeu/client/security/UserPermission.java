package com.ganchevdimitarg.client.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public enum UserPermission {
    CATALOG_READ("catalog.read"),
    CATALOG_WRITE("catalog.write"),
    PROFILE_READ("profile.read"),
    PROFILE_WRITE("profile.write"),
    ORDER_READ("order.read"),
    ORDER_WRITE("order.write"),
    NOTIFICATION_READ("notification.read"),
    NOTIFICATION_WRITE("notification.write");

    private final String permission;

    UserPermission(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}