package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class UpdateUserTest extends BaseTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void updateUser_happyPath_savesUpdatedProfile() {
        // Arrange
        Profile existing = Profile.builder()
                .id("1").username("old@example.com")
                .address(new Address("OldCity", "OldStreet", "0000"))
                .build();
        UserRequestDto request = new UserRequestDto("new@example.com", "NewPass@1",
                "Ivan", "Ivanov", "+359888000111", "Varna", "Main St", "9000",
                null, null, null, null);

        when(profileDao.findByUsername("old@example.com")).thenReturn(Mono.just(existing));
        when(passwordEncoder.encode("NewPass@1")).thenReturn("encodedNew");
        when(profileDao.save(any())).thenReturn(Mono.just(existing));

        // Act
        Mono<Void> result = profileService.updateUser("old@example.com", request);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(profileDao).save(argThat(p ->
                p.getUsername().equals("new@example.com") &&
                        p.getAddress().city().equals("Varna")));
    }

    @Test
    void updateUser_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        when(profileDao.findByUsername("ghost@example.com")).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.updateUser("ghost@example.com",
                new UserRequestDto("ghost@example.com", "Pass@1234", "Ivan", "Ivanov",
                        "+359888000111", "Varna", "Main St", "9000",
                        null, null, null, null));

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof UsernameNotFoundException)
                .verify();
    }
}
