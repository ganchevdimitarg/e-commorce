package com.concordeu.client.security;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public enum UserRole {
    ADMIN(Sets.newHashSet(
            UserPermission.CATALOG_READ,
            UserPermission.CATALOG_WRITE,
            UserPermission.PROFILE_READ,
            UserPermission.PROFILE_WRITE,
            UserPermission.ORDER_READ,
            UserPermission.ORDER_WRITE,
            UserPermission.NOTIFICATION_READ,
            UserPermission.NOTIFICATION_WRITE)),
    WORKER(Sets.newHashSet(
            UserPermission.CATALOG_READ,
            UserPermission.PROFILE_READ,
            UserPermission.ORDER_READ,
            UserPermission.NOTIFICATION_READ)),
    USER(Sets.newHashSet(
            UserPermission.CATALOG_READ,
            UserPermission.PROFILE_READ,
            UserPermission.PROFILE_WRITE,
            UserPermission.ORDER_READ,
            UserPermission.ORDER_WRITE,
            UserPermission.NOTIFICATION_READ,
            UserPermission.NOTIFICATION_WRITE));

    private final Set<UserPermission> permissions;

    public Set<SimpleGrantedAuthority> getGrantedAuthorities() {
        Set<SimpleGrantedAuthority> permissions = getPermissions().stream().map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        permissions.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return permissions;
    }
}

