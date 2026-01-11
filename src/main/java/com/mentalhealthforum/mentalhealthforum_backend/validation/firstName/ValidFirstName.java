package com.mentalhealthforum.mentalhealthforum_backend.validation.firstName;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD,  ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {}) // No custom validator needed yet
@Size(max = 50, message = "First name cannot exceed 50 characters.")
@Pattern(regexp = "^\\p{L}[\\p{L} '.-]*\\p{L}?$",
        message = "First name can only contain letters, spaces, apostrophes, dots, and hyphens, and must start with a letter")
public @interface ValidFirstName {
    String message() default "Invalid first name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
