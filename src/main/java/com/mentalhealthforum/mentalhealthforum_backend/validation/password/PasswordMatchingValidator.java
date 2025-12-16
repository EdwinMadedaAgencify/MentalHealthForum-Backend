package com.mentalhealthforum.mentalhealthforum_backend.validation.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Objects;

public class PasswordMatchingValidator implements ConstraintValidator<PasswordMatching, Object> {
    private String passwordField;
    private String confirmPasswordField;

    @Override
    public void initialize(PasswordMatching constraintAnnotation) {
        this.passwordField = constraintAnnotation.passwordField();
        this.confirmPasswordField = constraintAnnotation.confirmPassword();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if(value == null){
            return true; // Let other validators handle null
        }
        try {
            Object passwordValue = new BeanWrapperImpl(value).getPropertyValue(passwordField);
            Object confirmPasswordValue= new BeanWrapperImpl(value).getPropertyValue(confirmPasswordField);

            if(!Objects.equals(passwordValue, confirmPasswordValue)){
                // Add error to confirmPassword field for better UX
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        context.getDefaultConstraintMessageTemplate()
                )
                        .addPropertyNode(confirmPasswordField)
                        .addConstraintViolation();

                return false;
            }

            return true;
        } catch(Exception e){
            return true;
        }
    }
}
