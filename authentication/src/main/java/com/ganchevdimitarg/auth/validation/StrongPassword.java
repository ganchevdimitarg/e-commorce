package com.ganchevdimitarg.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Reusable password-complexity constraint: requires a digit, a lowercase letter, an
 * uppercase letter, a special character ({@code @#$%^&+=}), no whitespace, and a
 * length between 6 and 30 characters. Pair with {@code @NotBlank} at the call site —
 * this constraint returns {@code false} for {@code null}.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface StrongPassword {

    String message() default "password does not meet complexity rules";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
