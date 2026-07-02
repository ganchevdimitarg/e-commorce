package com.ganchevdimitarg.profile.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ControllerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorMessage> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        ErrorMessage message = new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                ex.getMessage(),
                exchange.getRequest().getPath().value());

        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidRequestDataException.class)
    public ResponseEntity<ErrorMessage> handleInvalidRequestData(
            InvalidRequestDataException ex, ServerWebExchange exchange) {
        ErrorMessage message = new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                ex.getMessage(),
                exchange.getRequest().getPath().value());

        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorMessage> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorMessage message = new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                errors,
                exchange.getRequest().getPath().value());

        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorMessage> handleAccessDenied(
            AccessDeniedException ex, ServerWebExchange exchange) {
        ErrorMessage message = new ErrorMessage(
                HttpStatus.FORBIDDEN.value(),
                LocalDateTime.now(),
                ex.getMessage(),
                exchange.getRequest().getPath().value());

        return new ResponseEntity<>(message, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorMessage> handleGenericException(
            Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception at {}: {}",
                exchange.getRequest().getPath(), ex.getMessage(), ex);

        ErrorMessage message = new ErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now(),
                "An unexpected error occurred",
                exchange.getRequest().getPath().value());

        return new ResponseEntity<>(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}