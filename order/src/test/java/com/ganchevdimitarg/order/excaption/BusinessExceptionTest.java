package com.ganchevdimitarg.order.excaption;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void should_carryStatusAndCode_forEachSubtype() {
        assertThat(new NotFoundException("Order not found: 1"))
                .returns(HttpStatus.NOT_FOUND, BusinessException::getStatus)
                .returns("NOT_FOUND", BusinessException::getCode);
        assertThat(new ConflictException("nope"))
                .returns(HttpStatus.CONFLICT, BusinessException::getStatus)
                .returns("CONFLICT", BusinessException::getCode);
        assertThat(new ValidationException("bad"))
                .returns(HttpStatus.BAD_REQUEST, BusinessException::getStatus)
                .returns("VALIDATION_ERROR", BusinessException::getCode);
    }
}
