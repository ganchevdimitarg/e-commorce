package com.ganchevdimitarg.notification.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void should_rejectBlankRecipient_when_recipientMissing() {
        var dto = new NotificationDto("", "Subject", "A valid body over ten chars");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void should_rejectInvalidEmail_when_recipientNotAnEmail() {
        var dto = new NotificationDto("not-an-email", "Subject", "A valid body over ten chars");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void should_rejectShortBody_when_bodyUnderTenChars() {
        var dto = new NotificationDto("user@test.com", "Subject", "short");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void should_pass_when_allFieldsValid() {
        var dto = new NotificationDto("user@test.com", "Subject", "A valid body over ten chars");
        assertThat(validator.validate(dto)).isEmpty();
    }
}
