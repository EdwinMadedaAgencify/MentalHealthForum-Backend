package com.mentalhealthforum.mentalhealthforum_backend.validation.group;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AssignableGroupValidator implements ConstraintValidator<ValidAssignableGroup, GroupPath> {

    @Override
    public boolean isValid(GroupPath group, ConstraintValidatorContext context) {
        if(group == null) return true; // @NotNull handles null

        // 1. Technical Check: Is it a leaf group that grants roles?
        if(!group.isAssignable()){
            buildViolation(context, String.format(
                    "Group '%s' is not assignable. Please select a subgroup like 'MEMBERS_NEW', 'MEMBERS_TRUSTED', etc.", group.getPath()
            ));
            return false;
        }

        // 2. Business Check: Is it a group an Admin is allowed to manually assign?
        if(!group.isManuallyAssignable()){
            buildViolation(context, String.format(
                    "Group '%s' is managed by the system reputation logic and cannot be assigned manually.", group.getDisplayName()
            ));
            return false;
        }
        return true;
    }

    private void buildViolation(ConstraintValidatorContext context, String message){
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
