package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserCredentialRepository repository;

    @InjectMocks
    private UserService userService;

    @Test
    void should_returnCredentialUserDetails_when_userExists() {
        UUID id = UUID.randomUUID();
        UserCredential credential = new UserCredential();
        credential.setId(id);
        credential.setEmail("user@test.io");
        credential.setPasswordHash("encodedPassword");
        credential.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        credential.setEnabled(true);
        when(repository.findByEmailAndDeletedAtIsNull("user@test.io")).thenReturn(Optional.of(credential));

        CredentialUserDetails result =
                (CredentialUserDetails) userService.loadUserByUsername("user@test.io");

        assertThat(result.getUsername()).isEqualTo(id.toString());
        assertThat(result.email()).isEqualTo("user@test.io");
        assertThat(result.getPassword()).isEqualTo("encodedPassword");
        assertThat(result.getAuthorities()).hasSize(2);
        assertThat(result.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("user@test.io");
    }

    @Test
    void should_throwUsernameNotFound_when_userMissing() {
        when(repository.findByEmailAndDeletedAtIsNull("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No such user");

        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("nonexistent");
    }

    @Test
    void should_returnNoAuthorities_when_userHasNoRoles() {
        UUID id = UUID.randomUUID();
        UserCredential credential = new UserCredential();
        credential.setId(id);
        credential.setEmail("noroles@test.io");
        credential.setPasswordHash("password");
        credential.setRoles(Set.of());
        credential.setEnabled(true);
        when(repository.findByEmailAndDeletedAtIsNull("noroles@test.io")).thenReturn(Optional.of(credential));

        CredentialUserDetails result =
                (CredentialUserDetails) userService.loadUserByUsername("noroles@test.io");

        assertThat(result.getUsername()).isEqualTo(id.toString());
        assertThat(result.getAuthorities()).isEmpty();
        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("noroles@test.io");
    }

    @Test
    void should_delegateToRepository_when_usernameNull() {
        when(repository.findByEmailAndDeletedAtIsNull(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(repository, times(1)).findByEmailAndDeletedAtIsNull(null);
    }
}
