package com.ganchevdimitarg.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerExceptionHandlerTest {

    @Test
    void should_returnProblemDetailWithCode_when_businessExceptionThrown() {
        ControllerExceptionHandler handler = new ControllerExceptionHandler();
        var response = handler.handleBusinessException(new MailDeliveryException("user@test.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "MAIL_DELIVERY_FAILED");
    }
}
