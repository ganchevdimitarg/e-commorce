package com.ganchevdimitarg.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class SecurityConfigTest {

    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrations;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void should_permitHealthProbe_when_unauthenticated() {
        // 200/2xx/5xx (anything but 3xx redirect) proves the request cleared the security layer
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().value(status -> {
                    boolean isNotRedirect = status < 300 || status >= 400;
                    assertThat(isNotRedirect).as("should not be 3xx redirect").isTrue();
                });
    }

    @Test
    void should_denyOtherActuatorEndpoints_when_unauthenticated() {
        // unauthenticated + protected: redirected towards login, never a 2xx/404
        webTestClient.get().uri("/actuator/metrics").exchange()
                .expectStatus().is3xxRedirection();
    }
}
