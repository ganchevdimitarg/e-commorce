package com.concordeu.profile.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum UserPermission {
    ADMIN_READ("admin:read"),
    ADMIN_WRITE("admin:write"),
    WORKER_READ("worker:read"),
    WORKER_WRITE("worker:write"),
    USER_READ("user:read"),
    USER_WRITE("user:write");



    private final String permission;
}
