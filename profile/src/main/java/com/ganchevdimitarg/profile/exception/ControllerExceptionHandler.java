package com.ganchevdimitarg.profile.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ControllerExceptionHandler {

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorMessage> handleServerWebInput(
            ServerWebInputException ex, ServerWebExchange exchange) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        ex.getReason() != null ? ex.getReason() : "Invalid request",
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorMessage> handleUsernameNotFound(
            UsernameNotFoundException ex, ServerWebExchange exchange) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorMessage(
                        HttpStatus.NOT_FOUND.value(),
                        LocalDateTime.now(),
                        ex.getMessage(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public Mono<ResponseEntity<ErrorMessage>> handleAccessDenied(
            RuntimeException ex, ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    // Has a real authenticated principal → 403
                    if (auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                        return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(new ErrorMessage(
                                        HttpStatus.FORBIDDEN.value(),
                                        LocalDateTime.now(),
                                        "Access Denied",
                                        exchange.getRequest().getPath().value()));
                    }
                    // Anonymous → 401
                    return ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorMessage(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    LocalDateTime.now(),
                                    "Unauthorized",
                                    exchange.getRequest().getPath().value()));
                })
                // No security context at all → 401
                .defaultIfEmpty(ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorMessage(
                                HttpStatus.UNAUTHORIZED.value(),
                                LocalDateTime.now(),
                                "Unauthorized",
                                exchange.getRequest().getPath().value())));
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorMessage> handleMissingRequestValue(
            MissingRequestValueException ex, ServerWebExchange exchange) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        ex.getReason(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorMessage> handleConstraintViolation(
            ConstraintViolationException ex, ServerWebExchange exchange) {
        String errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        errors,
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorMessage> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        ex.getMessage(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(InvalidRequestDataException.class)
    public ResponseEntity<ErrorMessage> handleInvalidRequestData(
            InvalidRequestDataException ex, ServerWebExchange exchange) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        ex.getMessage(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorMessage> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .badRequest()
                .body(new ErrorMessage(
                        HttpStatus.BAD_REQUEST.value(),
                        LocalDateTime.now(),
                        errors,
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorMessage> handleGenericException(
            Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception at {}: {}",
                exchange.getRequest().getPath(), ex.getMessage(), ex);

        return ResponseEntity
                .internalServerError()
                .body(new ErrorMessage(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        LocalDateTime.now(),
                        "An unexpected error occurred",
                        exchange.getRequest().getPath().value()));
    }
}