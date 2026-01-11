package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UserDoesNotExistException extends ApiException{

    public UserDoesNotExistException(){
        super(ErrorCode.USER_DOES_NOT_EXIST);
    }

    public UserDoesNotExistException(String message){
        super(message, ErrorCode.USER_DOES_NOT_EXIST);
    }
}
