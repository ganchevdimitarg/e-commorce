package com.ganchevdimitarg.catalog.exception;

import com.ganchevdimitarg.catalog.exception.BusinessException;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class BusinessExceptionTest {

    @Test
    void should_carryStatusAndCode_when_baseExceptionCreated() {
        BusinessException ex = new BusinessException(HttpStatus.I_AM_A_TEAPOT, "TEAPOT", "short and stout");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(ex.getCode()).isEqualTo("TEAPOT");
        assertThat(ex.getMessage()).isEqualTo("short and stout");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_be404WithNotFoundCode_when_notFoundException() {
        NotFoundException ex = new NotFoundException("Product", "mouse");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Product not found: mouse");
        assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_be409WithConflictCode_when_conflictException() {
        ConflictException ex = new ConflictException("already exists");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("CONFLICT");
        assertThat(ex.getMessage()).isEqualTo("already exists");
    }

    @Test
    void should_be400WithValidationCode_when_validationException() {
        ValidationException ex = new ValidationException("bad input");

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getMessage()).isEqualTo("bad input");
    }
}
