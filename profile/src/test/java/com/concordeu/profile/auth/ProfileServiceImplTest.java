package com.concordeu.profile.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {
/*

    ProfileService testService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    WebClient webClient;
    @Mock
    Gson mapper;
    @Mock
    ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    @Mock
    ProfileRepository profileRepository;
    @Mock
    JwtService jwtService;
    @Mock
    MailService mailService;
    @Mock
    ValidateData validateData;

    UserRequestDto model;
    Profile profile;

    @BeforeEach
    void setUp() {
        testService = new ProfileServiceImpl(
                passwordEncoder,
                webClient,
                mapper,
                reactiveCircuitBreakerFactory,
                profileRepository,
                jwtService,
                mailService,
                validateData);

        model = new UserRequestDto(
                "dimitarggacnhev333@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "0888888888",
                "Varna",
                "Katay",
                "9000",
                "4242424242424242",
                8,
                2030,
                "333");

        profile = Profile.builder()
                .id("1")
                .username("example@gmail.com")
                .grantedAuthorities(new HashSet<>())
                .firstName("ivan").lastName("ivanov")
                .address(new Address("varna", "katya", "9000"))
                .build();

    }

    @Test
    @Disabled
    void createUserShouldCreateUserIfUserNotExist() {
        when(profileRepository.findByUsername(model.username())).thenReturn(Optional.empty());
        testService.createUser(model);

        ArgumentCaptor<Profile> argumentCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).insert(argumentCaptor.capture());

        Profile captorProfile = argumentCaptor.getValue();
        assertThat(captorProfile).isNotNull();
        assertThat(captorProfile.getUsername()).isEqualTo(model.username());
    }

    @Test
    void createUserShouldThrowExceptionIfUserExist() {
        when(profileRepository.findByUsername(model.username())).thenReturn(Optional.of(Profile.builder().build()));

        assertThatThrownBy(() -> testService.createUser(model))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Profile already exist: " + model.username());

        verify(profileRepository, never()).insert(any(Profile.class));
    }

    @Test
    void updateUserShouldUpdateUserInfo() {
        Profile profileBefore = Profile.builder().firstName("Dimitar").build();
        when(profileRepository.findByUsername(model.username())).thenReturn(Optional.of(profileBefore));

        testService.updateUser(model.username(), model);

        ArgumentCaptor<Profile> argumentCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(argumentCaptor.capture());

        Profile profileAfter = argumentCaptor.getValue();
        assertThat(profileAfter).isNotNull();
        assertThat(profileAfter).isEqualTo(profileBefore);
        assertThat(profileAfter.getUsername()).isEqualTo(model.username());
    }

    @Test
    void updateUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.updateUser("", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void updateUserShouldThrowExceptionIfUserDoesNotExist() {
        when(profileRepository.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.updateUser("ivan@gmail.com", model))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    @Disabled
    void deleteUserShouldDeleteUser() {
        when(profileRepository.findByUsername(model.username())).thenReturn(Optional.of(Profile.builder().build()));

        testService.deleteUser(model.username());

        verify(profileRepository).delete(any(Profile.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.deleteUser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfUserDoesNotExist() {
        when(profileRepository.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.deleteUser("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileRepository, never()).delete(any(Profile.class));
    }

    @Test
    @Disabled
    void getUserByUsernameShouldReturnUser() {

        when(profileRepository.findByUsername(model.username())).thenReturn(Optional.of(profile));

        UserDto testUser = testService.getUserByUsername(model.username());

        assertThat(testUser.username()).isEqualTo(profile.getUsername());
    }

    @Test
    void getUserByUsernameShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.getUserByUsername(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void getUserByUsernameShouldThrowExceptionIfUserDoesNotExist() {
        when(profileRepository.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getUserByUsername("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileRepository, never()).delete(any(Profile.class));
    }
*/

}