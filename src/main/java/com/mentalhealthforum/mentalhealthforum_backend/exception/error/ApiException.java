package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ErrorDetail;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ApiException extends RuntimeException{

    private final ErrorCode errorCode;
    private final List<ErrorDetail> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode.getDescription(), errorCode, null);
    }

    public ApiException(String message, ErrorCode errorCode, Throwable cause) {
        this(message, errorCode, null, cause);
    }

    public ApiException(String message, ErrorCode errorCode) {
        this(message, errorCode, null);
    }

    public ApiException(String message, ErrorCode errorCode, List<ErrorDetail> details, Throwable cause) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
