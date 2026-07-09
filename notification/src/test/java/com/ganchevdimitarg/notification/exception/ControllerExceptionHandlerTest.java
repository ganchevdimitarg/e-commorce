package com.ganchevdimitarg.notification.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void should_returnProblemDetailWithCode_when_businessExceptionThrown() {
        var response = handler.handleBusinessException(new MailDeliveryException("user@test.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "MAIL_DELIVERY_FAILED");
    }

    @Test
    void should_mapSubclassesToTheirStatus_when_businessExceptionThrown() {
        assertThat(handler.handleBusinessException(new NotFoundException("Notification", 42))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleBusinessException(new ConflictException("duplicate notification"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleBusinessException(new ValidationException("recipient is blank"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return400WithValidationCode_when_constraintViolation() {
        var response = handler.handleConstraintViolation(
                new ConstraintViolationException("recipient: must not be blank", Set.of()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void should_return500WithoutLeakingDetails_when_unexpectedException() {
        var response = handler.handleUnexpected(new IllegalStateException("internal secret"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).doesNotContain("internal secret");
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void should_writeProblemJson401_when_unauthenticated() throws Exception {
        // Boot's auto-configured mapper registers the JavaTime module; mirror that here.
        var entryPoint = new ProblemAuthenticationEntryPoint(
                new ObjectMapper().registerModule(new JavaTimeModule()));
        var response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
    }
}
