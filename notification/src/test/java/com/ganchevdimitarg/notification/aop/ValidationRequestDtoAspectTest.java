package com.ganchevdimitarg.notification.aop;

import com.ganchevdimitarg.notification.annotation.ValidationRequest;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.exception.InvalidRequestDataException;
import com.ganchevdimitarg.notification.validation.ValidateRequestImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationRequestDtoAspectTest {

    private interface DummyTarget {
        String handle(NotificationDto notificationDto);
    }

    private static class DummyTargetImpl implements DummyTarget {
        @Override
        @ValidationRequest
        public String handle(NotificationDto notificationDto) {
            return "handled: " + notificationDto.recipient();
        }
    }

    private DummyTarget proxiedTarget() {
        var factory = new AspectJProxyFactory(new DummyTargetImpl());
        factory.addAspect(new ValidationRequestDtoAspect(new ValidateRequestImpl()));
        return factory.getProxy();
    }

    @Test
    void should_rejectRequest_when_notificationDtoHasBlankRecipient() {
        DummyTarget target = proxiedTarget();
        NotificationDto invalidDto = new NotificationDto("", "Order", "You have successfully created an order.");

        assertThatThrownBy(() -> target.handle(invalidDto))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void should_proceedToTarget_when_notificationDtoIsValid() {
        DummyTarget target = proxiedTarget();
        NotificationDto validDto = new NotificationDto("user@example.com", "Order", "You have successfully created an order.");

        String result = target.handle(validDto);

        assertThat(result).isEqualTo("handled: user@example.com");
    }
}
