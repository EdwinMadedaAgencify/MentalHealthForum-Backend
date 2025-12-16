package com.mentalhealthforum.mentalhealthforum_backend.validation.group;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AssignableGroupValidator implements ConstraintValidator<ValidAssignableGroup, GroupPath> {

    @Override
    public boolean isValid(GroupPath group, ConstraintValidatorContext context) {
        if(group == null) return true; // @NotNull handles null

        if(!group.isAssignable()){
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    String.format("Group '%s' is not assignable. Please select a subgroup like 'MEMBERS_NEW', 'MEMBERS_TRUSTED', etc.",
                            group)
            ).addConstraintViolation();
            return false;
        }
        return true;
    }
}
