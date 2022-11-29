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
    ADMIN(Sets.newHashSet(UserPermission.CREATE_CATEGORY,
            UserPermission.DELETE_CATEGORY,
            UserPermission.GET_CATEGORY,
            UserPermission.MOVE_BETWEEN_CATEGORIES,
            UserPermission.CREATE_PRODUCT,
            UserPermission.DELETE_PRODUCT,
            UserPermission.UPDATE_PRODUCT,
            UserPermission.GET_PRODUCT,
            UserPermission.CREATE_COMMENT,
            UserPermission.DELETE_COMMENT,
            UserPermission.GET_COMMENT,
            UserPermission.AUTH_USER)),
    WORKER(Sets.newHashSet(UserPermission.GET_CATEGORY,
            UserPermission.MOVE_BETWEEN_CATEGORIES,
            UserPermission.UPDATE_PRODUCT,
            UserPermission.GET_PRODUCT,
            UserPermission.AUTH_USER)),
    USER(Sets.newHashSet(UserPermission.CREATE_COMMENT, UserPermission.AUTH_USER));

    private final Set<UserPermission> permissions;

    public Set<SimpleGrantedAuthority> getGrantedAuthorities() {
        Set<SimpleGrantedAuthority> permissions = getPermissions().stream().map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        permissions.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return permissions;
    }
}

