package com.mentalhealthforum.mentalhealthforum_backend.validation.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password meets strength requirements.
 * Must contain at least 8 characters, including:
 * - 1 digit
 * - 1 uppercase letter
 * - 1 lowercase letter
 * - 1 special character
 *
 * Uses the same regex as Keycloak's password policy for consistency.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default PasswordPolicy.MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
