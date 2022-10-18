package com.concordeu.security;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


import java.util.Set;
import java.util.stream.Collectors;

import static com.concordeu.security.UserPermission.*;

@RequiredArgsConstructor
@Getter
public enum UserRole {
    ADMIN(Sets.newHashSet(CREATE_CATEGORY,
            DELETE_CATEGORY,
            GET_CATEGORY,
            MOVE_BETWEEN_CATEGORIES,
            CREATE_PRODUCT,
            DELETE_PRODUCT,
            UPDATE_PRODUCT,
            GET_PRODUCT,
            CREATE_COMMENT,
            DELETE_COMMENT,
            GET_COMMENT)),
    WORKER(Sets.newHashSet(GET_CATEGORY,
            MOVE_BETWEEN_CATEGORIES,
            UPDATE_PRODUCT,
            GET_PRODUCT)),
    USER(Sets.newHashSet(CREATE_COMMENT));

    private final Set<UserPermission> permissions;

    public Set<SimpleGrantedAuthority> getGrantedAuthorities() {
        Set<SimpleGrantedAuthority> permissions = getPermissions().stream().map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        permissions.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return permissions;
    }
}

