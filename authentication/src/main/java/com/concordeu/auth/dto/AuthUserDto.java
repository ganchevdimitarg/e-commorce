package com.concordeu.auth.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

public record AuthUserDto(String username, String password, Set<? extends GrantedAuthority> grantedAuthorities) {
}
