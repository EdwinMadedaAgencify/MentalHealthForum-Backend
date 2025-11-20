package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvalidPaginationException extends ApiException {
    public InvalidPaginationException(String message) {
        super(message, ErrorCode.INVALID_PAGINATION);
    }

    public InvalidPaginationException(String message , Throwable cause) {
        super(message, ErrorCode.INVALID_PAGINATION, cause);
    }
}
