package com.concordeu.profile.security;

import com.concordeu.client.common.ProfileGrantedAuthority;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    public Set<ProfileGrantedAuthority> getGrantedAuthorities() {
        Set<ProfileGrantedAuthority> permissions = getPermissions().stream().map(permission -> new ProfileGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        permissions.add(new ProfileGrantedAuthority("ROLE_" + this.name()));
        return permissions;
    }
}

