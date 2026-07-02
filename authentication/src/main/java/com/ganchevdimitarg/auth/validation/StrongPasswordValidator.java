package com.ganchevdimitarg.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates password complexity: at least one digit, one lowercase letter, one
 * uppercase letter, one special character ({@code @#$%^&+=}), no whitespace, and a
 * length between 6 and 30 characters.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern COMPLEXITY_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,30}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && COMPLEXITY_PATTERN.matcher(value).matches();
    }
}
