package com.ganchevdimitarg.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void should_returnProblemJsonWithCode_when_businessExceptionThrown() {
        BusinessException ex = new NotFoundException("Client", "abc");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsKey("code");
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void should_map500_when_clientConfigurationExceptionThrown() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusinessException(new ClientConfigurationException("no token settings"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("CLIENT_MISCONFIGURED");
    }
}
