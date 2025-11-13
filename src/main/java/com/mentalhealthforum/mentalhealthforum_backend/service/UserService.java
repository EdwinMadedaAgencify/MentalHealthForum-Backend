package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.RegisterUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ResetPasswordRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.UpdateUserProfileRequest;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.PasswordMismatchException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import org.keycloak.representations.idm.UserRepresentation;


public interface UserService {
    String registerUser(RegisterUserRequest registerUserRequest) throws UserExistsException, InvalidPasswordException;

    UserRepresentation updateUserProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest) throws UserExistsException, UserDoesNotExistException;

    void resetPassword(String userId, ResetPasswordRequest resetPasswordRequest) throws PasswordMismatchException, InvalidPasswordException, UserDoesNotExistException;

    void deleteUser(String userId) throws UserDoesNotExistException;

    UserRepresentation getUser(String userId) throws UserDoesNotExistException;

    PaginatedResponse<UserRepresentation> getAllUsers(int page, int size);
}
