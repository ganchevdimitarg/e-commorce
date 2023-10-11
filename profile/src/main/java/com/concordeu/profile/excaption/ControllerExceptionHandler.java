package com.concordeu.profile.excaption;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@ControllerAdvice(basePackages = "com.concordeu.profile.controller")
public class ControllerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> resourceNotFoundException(IllegalArgumentException ex, WebRequest request) {
        /*ErrorMessage message = new ErrorMessage(
                HttpStatus.NOT_FOUND.value(),
                OffsetDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));*/
        ProblemDetail message = ProblemDetail.forStatus(HttpStatus.NOT_FOUND.value());
        message.setDetail(ex.getLocalizedMessage());

        return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(WebClientResponseException.InternalServerError.class)
    public ResponseEntity<ProblemDetail> resourceNotFoundException(WebClientResponseException.InternalServerError ex, WebRequest request) {
        /*ErrorMessage message = new ErrorMessage(
                HttpStatus.NOT_FOUND.value(),
                OffsetDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));*/
        ProblemDetail message = ProblemDetail.forStatus(HttpStatus.NOT_FOUND.value());
        message.setDetail(ex.getLocalizedMessage());

        return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidRequestDataException.class)
    public ResponseEntity<ProblemDetail> invalidRequestDataException(InvalidRequestDataException ex, WebRequest request) {
        /*ErrorMessage message = new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                OffsetDateTime.now(),
                ex.getMessage(),
                request.getDescription(false));*/
        ProblemDetail message = ProblemDetail.forStatus(HttpStatus.NOT_FOUND.value());
        message.setDetail(ex.getLocalizedMessage());

        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }
}