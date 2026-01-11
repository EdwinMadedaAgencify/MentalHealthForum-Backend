package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvitationAlreadyVerifiedException extends ApiException{
    public InvitationAlreadyVerifiedException(){
        super(ErrorCode.INVITATION_ALREADY_VERIFIED);
    }
}