package com.mentalhealthforum.mentalhealthforum_backend.validation.timezone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.ZoneId;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

    private static final Logger log = LoggerFactory.getLogger(TimezoneValidator.class);
    private boolean nullable;

    @Override
    public void initialize(ValidTimezone annotation) {
        this.nullable = annotation.nullable();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank()){
            return nullable;
        }

        try{
            ZoneId.of(value.trim());
            return true;
        } catch (DateTimeException e){
            log.debug("Invalid timezone: {}", value);

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Invalid timezone: '" + value + "'.Please select a valid timezone."
            ).addConstraintViolation();
            return false;
        }
    }

    public static String canonicalize(String timezone){
        if(timezone == null || timezone.isBlank()) return null;
        try{
            return ZoneId.of(timezone.trim()).normalized().getId();
        } catch(DateTimeException e){
            return null;
        }
    }
}
