package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvalidGroupAssignmentException extends ApiException {
    public InvalidGroupAssignmentException(String message) {
        super(message, ErrorCode.GROUP_ASSIGNMENT_VIOLATION);
    }
}
