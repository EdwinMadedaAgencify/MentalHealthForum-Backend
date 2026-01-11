package com.mentalhealthforum.mentalhealthforum_backend.validation.bio;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD,  ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Size(max = 500, message = "Bio cannot exceed 500 characters.")
public @interface ValidBio {
    String message() default "Invalid bio";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
