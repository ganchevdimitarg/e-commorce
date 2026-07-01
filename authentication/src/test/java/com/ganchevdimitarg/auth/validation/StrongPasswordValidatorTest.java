package com.ganchevdimitarg.auth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void should_returnTrue_when_passwordMeetsComplexityRules() {
        assertThat(validator.isValid("Aa1@aaaa", null)).isTrue();
    }

    @Test
    void should_returnFalse_when_passwordHasNoDigit() {
        assertThat(validator.isValid("Aa@aaaaa", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordHasNoUppercase() {
        assertThat(validator.isValid("aa1@aaaa", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordHasNoLowercase() {
        assertThat(validator.isValid("AA1@AAAA", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordHasNoSpecialCharacter() {
        assertThat(validator.isValid("Aa1aaaaa", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordTooShort() {
        assertThat(validator.isValid("Aa1@a", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordContainsWhitespace() {
        assertThat(validator.isValid("Aa1@ aaa", null)).isFalse();
    }

    @Test
    void should_returnFalse_when_passwordIsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
