package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateWorkerTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void createWorker_happyPath_returnsUserDtoWithWorkerAuthorities() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "worker@example.com", "Pass@1234", "Ivan", "Ivanov",
                "+359888000111", "Varna", "Main St", "9000",
                null, null, null, null);
        Profile savedProfile = Profile.builder()
                .id("1")
                .username("worker@example.com")
                .firstName("Ivan")
                .lastName("Ivanov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("+359888000111")
                .grantedAuthorities(WORKER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet())
                )
                .build();

        when(profileDao.findByUsername("worker@example.com")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(profileDao.insert(any(Profile.class))).thenReturn(Mono.just(savedProfile));

        // Act
        Mono<UserDto> result = profileService.createWorker(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo("worker@example.com");
                    assertThat(dto.cardId()).isEmpty();
                    assertThat(dto.grantedAuthorities())
                            .containsAll(WORKER.getGrantedAuthorities()
                                    .stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet())
                            );
                })
                .verifyComplete();
    }

    @Test
    void createWorker_duplicateUsername_throwsInvalidRequestDataException() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "worker@example.com", "Pass@1234", "Ivan", "Ivanov",
                "+359888000111", "Varna", "Main St", "9000",
                null, null, null, null);
        when(profileDao.findByUsername("worker@example.com"))
                .thenReturn(Mono.just(Profile.builder().build()));

        // Act
        Mono<UserDto> result = profileService.createWorker(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().contains("Profile already exists"))
                .verify();
    }
}