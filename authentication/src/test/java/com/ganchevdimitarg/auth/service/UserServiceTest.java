package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.AuthUserDao;
import com.ganchevdimitarg.auth.domain.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AuthUserDao authUserDao;

    @InjectMocks
    private UserService userService;

    @Test
    void should_returnUserDetails_when_userExists() {
        AuthUser authUser = AuthUser.builder()
                .username("user@test.io")
                .password("encodedPassword")
                .authorities(Set.of("ROLE_USER", "ROLE_ADMIN"))
                .build();
        when(authUserDao.findByUsername("user@test.io")).thenReturn(Optional.of(authUser));

        UserDetails result = userService.loadUserByUsername("user@test.io");

        assertNotNull(result);
        assertEquals("user@test.io", result.getUsername());
        assertEquals("encodedPassword", result.getPassword());
        assertEquals(2, result.getAuthorities().size());
        assertTrue(result.getAuthorities().stream()
                .map(Object::toString)
                .toList()
                .containsAll(Set.of("ROLE_USER", "ROLE_ADMIN")));
        verify(authUserDao, times(1)).findByUsername("user@test.io");
    }

    @Test
    void should_throwUsernameNotFound_when_userMissing() {
        when(authUserDao.findByUsername("nonexistent")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("nonexistent"));

        assertEquals("No such user", exception.getMessage());
        verify(authUserDao, times(1)).findByUsername("nonexistent");
    }

    @Test
    void should_returnNoAuthorities_when_userHasNoRoles() {
        AuthUser authUser = AuthUser.builder()
                .username("noroles@test.io")
                .password("password")
                .authorities(Set.of())
                .build();
        when(authUserDao.findByUsername("noroles@test.io")).thenReturn(Optional.of(authUser));

        UserDetails result = userService.loadUserByUsername("noroles@test.io");

        assertNotNull(result);
        assertEquals("noroles@test.io", result.getUsername());
        assertTrue(result.getAuthorities().isEmpty());
        verify(authUserDao, times(1)).findByUsername("noroles@test.io");
    }

    @Test
    void should_delegateToDao_when_usernameNull() {
        when(authUserDao.findByUsername(null)).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> userService.loadUserByUsername(null));

        verify(authUserDao, times(1)).findByUsername(null);
    }
}
