package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InsufficientPermissionException extends ApiException {

    public InsufficientPermissionException(String message) {
        super(message, ErrorCode.FORBIDDEN);
    }

    public InsufficientPermissionException(String message, Throwable cause) {
        super(message, ErrorCode.FORBIDDEN, cause);
    }
}
