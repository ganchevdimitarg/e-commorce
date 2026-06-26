package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServicePersistenceIT extends AbstractIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserCredentialRepository repository;

    @Test
    void should_loadByEmail_andExposeUserIdAsUsername_when_present() {
        UUID id = UUID.randomUUID();
        UserCredential c = new UserCredential();
        c.setId(id);
        c.setEmail("user@test.io");
        c.setPasswordHash("{noop}pw");
        c.setRoles(Set.of("ROLE_USER"));
        c.setEnabled(true);
        repository.save(c);

        CredentialUserDetails details =
                (CredentialUserDetails) userService.loadUserByUsername("user@test.io");

        assertThat(details.getUsername()).isEqualTo(id.toString());
        assertThat(details.email()).isEqualTo("user@test.io");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void should_throw_when_userAbsent() {
        assertThatThrownBy(() -> userService.loadUserByUsername("nobody@test.io"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
