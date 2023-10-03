package com.concordeu.profile.controller;

import com.concordeu.profile.service.KafkaProducerService;
import com.concordeu.profile.service.ProfileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;

@WebFluxTest(controllers = ProfileController.class)
@ActiveProfiles("test")
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ProfileControllerTest {
    @Autowired
    WebTestClient client;

    @MockBean
    ProfileService profileService;
    @MockBean
    KafkaProducerService mailService;
    @MockBean
    Authentication authentication;


    /*public static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:latest"));
    public static VaultContainer<?> vaultContainer = new VaultContainer<>(DockerImageName.parse("vault:latest"));
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    public static GenericContainer<?> authServer = new GenericContainer<>(DockerImageName.parse("dgganchev/authentication:latest"));

    @DynamicPropertySource
    public static void setProperty(DynamicPropertyRegistry registry) {
        Startables.deepStart(mongoDBContainer, kafka, authServer, vaultContainer).join();

        registry.add("spring.kafka.bootstrapA-server", kafka::getBootstrapServers);

        mongoDBContainer.start();

        vaultContainer
                .withVaultToken("test")
                .withSecretInVault("secret/profile-service/test",
                "ecommerce.oauth2.clientId=test",
                "ecommerce.oauth2.secret=test",
                "github.clientId=test",
                "github.secret=test",
                "jwt.secret.key=test",
                "spring.data.mongodb.password=test",
                "spring.data.mongodb.username=test",
                "spring.security.oauth2.client.registration.gateway-client-oidc.client-id=test",
                "spring.security.oauth2.client.registration.gateway-client-oidc.client-secret=test"
        );
    }*/

    @Test
    @WithMockUser
    void getUserByEmailShouldReturnUser() {
//        String username = "user";
//        when(profileService.getUserByUsername(username)).thenReturn(UserDto.builder().username(username).build());
//
//        this.client
//                .get()
//                .uri("/api/v1/profile/get-by-username?username={email}", username)
//                .accept(MediaType.APPLICATION_JSON)
//                .exchange()
//                .expectStatus().isOk();
    }

    @Test
    void registerUserShouldCreateUser() {
//        doNothing().when(mailService).sendUserWelcomeMail(any(String.class));
//        String username = "ivanIvanov@gmail.com";
//        when(profileService.createUser(UserRequestDto.builder().username(username).build())).thenReturn(UserDto.builder().username(username).build());
//        this.client
//                .mutateWith(csrf())
//                .mutateWith(mockJwt())
//                .post()
//                .uri("/api/v1/profile/register-user")
//                .contentType(MediaType.APPLICATION_JSON)
//                .accept(MediaType.APPLICATION_JSON)
//                .bodyValue("""
//                        {
//                            "username" : "ivanIvanov@gmail.com",
//                            "password" : "Abc123!@#",
//                            "firstName" : "Ivana",
//                            "lastName" : "Ivanov",
//                            "email" : "ivan@gmail.com",
//                            "phoneNumber" : "0888888888",
//                            "city" : "varna",
//                            "street" : "katay",
//                            "postCode" : "9000"
//                        }
//                        """)
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody().jsonPath("$.username", username);
    }

    @Test
    void registerAdminShouldCreateAdmin() {
        this.client.mutateWith(csrf())
                .mutateWith(mockOAuth2Login())
                .post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username" : "ivanIvanov@gmail.com",
                            "password" : "Abc123!@#",
                            "firstName" : "Ivana",
                            "lastName" : "Ivanov",
                            "email" : "ivan@gmail.com",
                            "phoneNumber" : "0888888888",
                            "city" : "varna",
                            "street" : "katay",
                            "postCode" : "9000"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void updateUserShouldUpdateUserInfo() {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .put()
                .uri("/api/v1/profile/update-user?username={email}", "ivan@gmail.com")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username" : "ivan@gmail.com",
                            "password" : "Abc123!@#",
                            "firstName" : "Ivanь",
                            "lastName" : "Ivanov",
                            "email" : "ivan@gmail.com",
                            "phoneNumber" : "0888888888",
                            "city" : "varna",
                            "street" : "katay",
                            "postCode" : "9000"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();
    }


    @Test
    void deleteUserShouldDeleteUser() {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .delete()
                .uri("/api/v1/profile/delete-user?username={email}", "ivan@gmail.com")
                .exchange()
                .expectStatus().isOk();
    }
}
