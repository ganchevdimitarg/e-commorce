package com.ganchevdimitarg.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public final class CredentialUserDetails implements UserDetails {

    private final String userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> roles;

    public CredentialUserDetails(String userId, String email, String passwordHash,
                                 boolean enabled, Set<String> roles) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
    }

    public String email() { return email; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return userId; }       // sub = userId
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
