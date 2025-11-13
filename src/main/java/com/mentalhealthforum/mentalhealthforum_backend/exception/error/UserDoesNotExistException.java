package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UserDoesNotExistException extends ApiException{
    public UserDoesNotExistException(String message){
        super(message, ErrorCode.USER_DOES_NOT_EXIST);
    }
}
