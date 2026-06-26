package com.ganchevdimitarg.auth.domain;

import java.util.Set;

public enum UserRole {
    ADMIN(Set.of("ROLE_ADMIN")),
    WORKER(Set.of("ROLE_WORKER")),
    USER(Set.of("ROLE_USER"));

    private final Set<String> authorities;

    UserRole(Set<String> authorities) { this.authorities = authorities; }

    public Set<String> authorities() { return authorities; }
}
