package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;

public class InvalidGroupAssignmentException extends ApiException {

    public InvalidGroupAssignmentException(GroupPath groupPath){
        super(
                String.format("Group %s is not assignable. Only leaf groups with role grants can be assigned.", groupPath),
                ErrorCode.GROUP_ASSIGNMENT_VIOLATION);
    }

    public InvalidGroupAssignmentException(String message) {
        super(message, ErrorCode.GROUP_ASSIGNMENT_VIOLATION);
    }

    public InvalidGroupAssignmentException(String message, Throwable cause) {
        super(message, ErrorCode.GROUP_ASSIGNMENT_VIOLATION, cause);
    }
}
