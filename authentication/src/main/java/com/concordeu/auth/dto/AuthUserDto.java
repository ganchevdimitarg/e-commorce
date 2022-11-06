package com.concordeu.auth.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

public record AuthUserDto(String email, String password, Set<? extends GrantedAuthority> grantedAuthorities) {
}
