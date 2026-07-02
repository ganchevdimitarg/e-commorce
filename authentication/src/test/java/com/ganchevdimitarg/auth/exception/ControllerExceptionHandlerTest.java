package com.ganchevdimitarg.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
        assertThat(response.getBody().getProperties()).containsKey("timestamp");
    }

    @Test
    void should_map500_when_clientConfigurationExceptionThrown() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBusinessException(new ClientConfigurationException("no token settings"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("CLIENT_MISCONFIGURED");
    }

    @Test
    void should_returnInternalError_when_unexpectedException() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void should_returnValidationProblemJsonWithErrorsMap_when_methodArgumentNotValid() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerUserCommand");
        bindingResult.addError(new FieldError("registerUserCommand", "password",
                "password does not meet complexity rules"));
        bindingResult.addError(new FieldError("registerUserCommand", "email", "must not be blank"));
        MethodParameter methodParameter = new MethodParameter(
                ControllerExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getProperties()).containsKey("timestamp");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) body.getProperties().get("errors");
        assertThat(errors)
                .containsEntry("password", "password does not meet complexity rules")
                .containsEntry("email", "must not be blank");
    }

    // Reflection target only — gives MethodParameter a real Executable so
    // MethodArgumentNotValidException#getMessage() does not NPE.
    private void dummyTarget(String password) {}
}
