package com.mentalhealthforum.mentalhealthforum_backend.validation.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation to validate that password and confirmPassword match.
 * Can be reused across multiple DTOs that have password confirmation fields.
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchingValidator.class)
public @interface PasswordMatching {
    String message() default "Password and confirmation password do not match";

    Class<?> [] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * The name of the password field (default: "password")
     */
    String passwordField() default "password";

    /**
     * The name of the confirm password field (default: "confirmPassword")
     */
    String confirmPassword() default "confirmPassword";

}
