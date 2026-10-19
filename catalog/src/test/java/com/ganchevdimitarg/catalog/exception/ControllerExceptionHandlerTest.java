package com.ganchevdimitarg.catalog.exception;

import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

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

    @Test
    void should_return403ProblemDetail_when_accessDenied() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getDetail())
                .isEqualTo("You do not have permission to access this resource");
        assertThat(body.getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void should_return401ProblemDetail_when_authenticationException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAuthentication(new BadCredentialsException("bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "UNAUTHENTICATED");
    }

    @Test
    void should_return409ProblemDetail_when_dataIntegrityViolation() {
        ResponseEntity<ProblemDetail> response =
                handler.handleIntegrityViolation(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "CONFLICT");
        assertThat(body.getDetail()).doesNotContain("duplicate key");
    }

    @Test
    void should_return400ProblemDetail_when_constraintViolation() {
        var violations = new java.util.HashSet<jakarta.validation.ConstraintViolation<?>>();
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException("size: must be <= 100", violations);

        org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail> response =
                handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }
}
