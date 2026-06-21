package com.concordeu.catalog.exception;

import com.concordeu.catalog.excaption.ConflictException;
import com.concordeu.catalog.excaption.ControllerExceptionHandler;
import com.concordeu.catalog.excaption.NotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void should_return404ProblemDetail_when_notFoundException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusinessException(new NotFoundException("Product", "mouse"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getDetail()).isEqualTo("Product not found: mouse");
        assertThat(body.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(body.getProperties().get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    void should_return409ProblemDetail_when_conflictException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusinessException(new ConflictException("already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "CONFLICT");
    }

    @Test
    void should_return500ProblemDetail_when_unexpectedException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
