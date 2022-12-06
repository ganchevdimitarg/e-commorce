package com.concordeu.auth.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum UserPermission {
    CREATE_CATEGORY("create:category"),
    DELETE_CATEGORY("delete:category"),
    GET_CATEGORY("get:category"),
    MOVE_BETWEEN_CATEGORIES("move:categories"),
    CREATE_PRODUCT("create:product"),
    DELETE_PRODUCT("delete:product"),
    UPDATE_PRODUCT("update:product"),
    GET_PRODUCT("get:product"),
    CREATE_COMMENT("create:comment"),
    DELETE_COMMENT("delete:comment"),
    GET_COMMENT("get:comment");

    private final String permission;
}
