package com.ganchevdimitarg.order.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void should_renderProblemJson_when_businessException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusinessException(new NotFoundException("Order not found: 7"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("Order not found: 7");
        assertThat(body.getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void should_renderProblemJson_when_optimisticLockException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException("Concurrent modification detected", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("The resource was modified concurrently; please retry");
        assertThat(body.getProperties()).containsEntry("code", "CONFLICT");
    }
}
