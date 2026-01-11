package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvalidPaginationException extends ApiException {
    public InvalidPaginationException() {
        super(ErrorCode.INVALID_PAGINATION);
    }
}
