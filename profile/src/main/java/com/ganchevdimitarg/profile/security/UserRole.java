package com.ganchevdimitarg.profile.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum UserRole {
    ADMIN(EnumSet.allOf(UserPermission.class)),
    WORKER(EnumSet.of(
            UserPermission.CATALOG_READ,
            UserPermission.PROFILE_READ,
            UserPermission.ORDER_READ,
            UserPermission.NOTIFICATION_READ)),
    USER(EnumSet.of(
            UserPermission.CATALOG_READ,
            UserPermission.PROFILE_READ,
            UserPermission.PROFILE_WRITE,
            UserPermission.ORDER_READ,
            UserPermission.ORDER_WRITE,
            UserPermission.NOTIFICATION_READ,
            UserPermission.NOTIFICATION_WRITE));

    private final Set<UserPermission> permissions;

    UserRole(Set<UserPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<UserPermission> getPermissions() {
        return permissions;
    }

    /**
     * Maps permissions to authorities; adds role; returns unmodifiable set
     */
    public Set<SimpleGrantedAuthority> getGrantedAuthorities() {
        Set<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermission()))
                .collect(Collectors.toSet());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return Collections.unmodifiableSet(authorities);
    }
}