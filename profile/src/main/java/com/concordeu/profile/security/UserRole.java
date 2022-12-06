package com.concordeu.profile.security;

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
            UserPermission.ADMIN_READ,
            UserPermission.ADMIN_WRITE)),
    WORKER(Sets.newHashSet(
            UserPermission.WORKER_READ,
            UserPermission.WORKER_WRITE)),
    USER(Sets.newHashSet(
            UserPermission.USER_READ,
            UserPermission.USER_WRITE));

    private final Set<UserPermission> permissions;

    public Set<SimpleGrantedAuthority> getGrantedAuthorities() {
        Set<SimpleGrantedAuthority> permissions = getPermissions().stream().map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        permissions.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return permissions;
    }
}

