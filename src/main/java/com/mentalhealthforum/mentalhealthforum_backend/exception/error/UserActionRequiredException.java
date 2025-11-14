package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;


/**
 * Custom exception thrown when a user successfully authenticates but has
 * pending required actions (e.g., VERIFY_EMAIL) before they can access the application.
 */
public class UserActionRequiredException extends ApiException{
    public UserActionRequiredException(String message) {
        super(message, ErrorCode.USER_ACTION_REQUIRED);
    }
}
