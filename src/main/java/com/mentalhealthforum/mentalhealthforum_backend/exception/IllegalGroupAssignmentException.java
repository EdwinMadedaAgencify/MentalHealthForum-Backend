package com.mentalhealthforum.mentalhealthforum_backend.exception;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;

public class IllegalGroupAssignmentException extends ApiException {
    public IllegalGroupAssignmentException(String groupDisplayName){
        super(
                String.format("The group '%s' is reserved for system-automated progression and cannot be assigned manually.", groupDisplayName),
                ErrorCode.ILLEGAL_GROUP_ASSIGNMENT
        );
    }
}
