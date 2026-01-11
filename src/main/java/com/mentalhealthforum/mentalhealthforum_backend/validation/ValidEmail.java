package com.mentalhealthforum.mentalhealthforum_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Size(max = 100, message = "Email cannot exceed 100 characters.")
//@Email(message = "Email must be a valid email format.")
// Additional pattern for stricter validation
@Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
        message = "Email must be a valid email format.")
public @interface ValidEmail {
    String message() default "Invalid email";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
