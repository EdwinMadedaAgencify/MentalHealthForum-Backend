package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(message, ErrorCode.TOO_MANY_REQUESTS);
    }
}
