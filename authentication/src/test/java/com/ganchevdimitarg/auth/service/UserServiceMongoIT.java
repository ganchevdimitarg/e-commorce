package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.AuthUserDao;
import com.ganchevdimitarg.auth.domain.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceMongoIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthUserDao authUserDao;

    @Test
    void should_loadUser_when_presentInMongo() {
        authUserDao.save(AuthUser.builder()
                .username("user@test.io")
                .password("{noop}pw")
                .authorities(Set.of("ROLE_USER"))
                .build());

        UserDetails details = userService.loadUserByUsername("user@test.io");

        assertThat(details.getUsername()).isEqualTo("user@test.io");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void should_throw_when_userAbsent() {
        assertThatThrownBy(() -> userService.loadUserByUsername("nobody@test.io"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
