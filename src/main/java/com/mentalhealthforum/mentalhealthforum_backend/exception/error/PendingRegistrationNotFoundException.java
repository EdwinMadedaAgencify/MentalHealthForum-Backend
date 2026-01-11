package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;


public class PendingRegistrationNotFoundException extends ApiException{
    public PendingRegistrationNotFoundException(){
        super(ErrorCode.PENDING_REGISTRATION_NOT_FOUND);
    }
}