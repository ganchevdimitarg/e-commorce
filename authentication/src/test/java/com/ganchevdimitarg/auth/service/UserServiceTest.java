package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.AuthUserDao;
import com.ganchevdimitarg.auth.domain.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AuthUserDao authUserDao;

    @InjectMocks
    private UserService userService;

    @Test
    void loadUserByUsername_WhenUserExists_ShouldReturnUserDetails() {
        // Arrange
        String username = "testuser";
        String password = "encodedPassword";
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );

        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUsername()).thenReturn(username);
        when(authUser.getPassword()).thenReturn(password);
        when(authUser.getGrantedAuthorities()).thenAnswer(invocation -> authorities);

        when(authUserDao.findByUsername(username)).thenReturn(Optional.of(authUser));

        // Act
        UserDetails result = userService.loadUserByUsername(username);

        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(password, result.getPassword());
        assertEquals(2, result.getAuthorities().size());
        assertTrue(result.getAuthorities().containsAll(authorities));
        verify(authUserDao, times(1)).findByUsername(username);
    }

    @Test
    void loadUserByUsername_WhenUserNotFound_ShouldThrowUsernameNotFoundException() {
        // Arrange
        String username = "nonexistent";
        when(authUserDao.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userService.loadUserByUsername(username)
        );

        assertEquals("No such user", exception.getMessage());
        verify(authUserDao, times(1)).findByUsername(username);
    }

    @Test
    void loadUserByUsername_WithEmptyAuthorities_ShouldReturnUserDetailsWithNoAuthorities() {
        // Arrange
        String username = "userwithoutroles";
        String password = "password";
        Set<GrantedAuthority> emptyAuthorities = Set.of();

        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUsername()).thenReturn(username);
        when(authUser.getPassword()).thenReturn(password);
        when(authUser.getGrantedAuthorities()).thenAnswer(invocation -> new HashSet<>());

        when(authUserDao.findByUsername(username)).thenReturn(Optional.of(authUser));

        // Act
        UserDetails result = userService.loadUserByUsername(username);

        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertTrue(result.getAuthorities().isEmpty());
        verify(authUserDao, times(1)).findByUsername(username);
    }

    @Test
    void loadUserByUsername_WithNullUsername_ShouldDelegateToDao() {
        // Arrange
        when(authUserDao.findByUsername(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                UsernameNotFoundException.class,
                () -> userService.loadUserByUsername(null)
        );

        verify(authUserDao, times(1)).findByUsername(null);
    }
}