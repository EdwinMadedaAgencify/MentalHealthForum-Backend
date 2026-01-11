package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.AppUserVerificationPayload;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.OtpPayload;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.SelfRegPayload;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationRepository;
import com.mentalhealthforum.mentalhealthforum_backend.model.PendingUser;
import com.mentalhealthforum.mentalhealthforum_backend.repository.PendingUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.VerificationTokenRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.*;
import com.mentalhealthforum.mentalhealthforum_backend.utils.EncryptionUtils;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.ChangeUtils.setIfChangedStrict;


@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final KeycloakAdminManager adminManager;
    private final KeycloakUserDtoMapper keycloakUserDtoMapper;
    private final EncryptionUtils encryptionUtils;
    private final PendingUserRepository pendingUserRepository;
    private final VerificationTokenService tokenWorker;
    private final VerificationService verificationService;
    private final AdminInvitationService adminInvitationService;
    private final OtpWorker otpWorker;
    private final NovuService novuService;

    // Inject the new KeycloakAdminManagerImpl
    public UserServiceImpl(
            KeycloakAdminManager adminManager,
            KeycloakUserDtoMapper keycloakUserDtoMapper,
            EncryptionUtils encryptionUtils,
            PendingUserRepository pendingUserRepository,
            VerificationTokenRepository verificationTokenRepository,
            VerificationTokenService tokenWorker,
            VerificationService verificationService,
            AdminInvitationService adminInvitationService, AdminInvitationRepository adminInvitationRepository,
            OtpWorker otpWorker,
            NovuService novuService) {
        this.adminManager = adminManager;
        this.keycloakUserDtoMapper = keycloakUserDtoMapper;
        this.encryptionUtils = encryptionUtils;
        this.pendingUserRepository = pendingUserRepository;
        this.tokenWorker = tokenWorker;
        this.verificationService = verificationService;
        this.adminInvitationService = adminInvitationService;
        this.otpWorker = otpWorker;
        this.novuService = novuService;
    }

    // ------------------ Public API Methods (Reactive Wrappers) ------------------
    @Override
    public Mono<String> createUserInStaging(RegisterUserRequest registerUserRequest) {
        String username = registerUserRequest.username().trim();
        String email = registerUserRequest.email().trim().toLowerCase();

        // Run blocking Keycloak Admin Client logic on a dedicated thread pool
        // Step 1: Global Identity Check (Blocking Keycloak calls)
        return Mono.fromCallable(()-> {
            // Check Keycloak (Global Identity)
            if (adminManager.findUserByUsername(username).isPresent()) {
                throw new UserExistsException("An account already exists for this username.");
            }

            if (adminManager.findUserByEmail(email).isPresent()) {
                throw new UserExistsException("An account already exists for this email.");
            }
            return true;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(unused ->
                        // Step 2: Rate-Limit Check (Don't delete yet, just check)
                       tokenWorker.checkRateLimit(email)
                                // Step 3: Consolidated Wipe (Parallel execution)
                                .then(Mono.when(
                                     tokenWorker.removeToken(email),
                                     pendingUserRepository.deleteByUsernameOrEmail(username, email)
                                ))
                                // Step 4: Create Staging State
                                .then(Mono.defer(()-> {
                                    String encryptedPassword = encryptionUtils.encrypt(registerUserRequest.password());
                                    PendingUser pendingUser = new PendingUser(
                                            null,
                                            username,
                                            email,
                                            encryptedPassword,
                                            registerUserRequest.firstName(),
                                            registerUserRequest.lastName()
                                    );

                                    return pendingUserRepository.save(pendingUser);
                                }))
                )
                .flatMap(savedPending ->
                    // Step 5: Generate Link and Trigger Communication
                    verificationService.createVerificationLink(
                            email,
                            VerificationType.SELF_REG,
                            GroupPath.MEMBERS_NEW.getPath(),
                            null
                    )
                    .flatMap(verificationLink -> {
                        SelfRegPayload payload = new SelfRegPayload(
                                registerUserRequest.firstName(),
                                verificationLink
                        );

                        return novuService.triggerEvent(NovuWorkflow.SELF_REG_VERIFICATION, email, email, payload)
                                .thenReturn(email); // Final Success
                    })
                );
    }

    @Override
    public Mono<KeycloakUserDto> createUserInKeycloak(PendingUser pendingUser, String groupPath) {
        return Mono.fromCallable(()-> {
            // Decrypt the password we staged earlier
            String rawPassword = encryptionUtils.decrypt(pendingUser.encryptedPassword());

            UserRepresentation userRep = new UserRepresentation();
            userRep.setEnabled(true);
            userRep.setUsername(pendingUser.username());
            userRep.setEmail(pendingUser.email());
            userRep.setFirstName(pendingUser.firstName());
            userRep.setLastName(pendingUser.lastName());
            userRep.setEmailVerified(true); // Verified by clicking the link

            var passwordCred = adminManager.createPasswordCredential(rawPassword);
            userRep.setCredentials(Collections.singletonList(passwordCred));

            // Create in Keycloak
            String userId = adminManager.createUser(userRep);

            // Assign to the group specified in the token (e.g., MEMBERS_NEW)
            adminManager.assignUserToGroup(userId, GroupPath.MEMBERS_NEW);
            adminManager.markAsSyncedLocally(userId, false);
            adminManager.assignInternalRole(userId, InternalRole.ONBOARDING);

            return userId;
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::getUser);
    }

    // Define the internal "Assembly Line" package
    private record ProfileUpdateContext(
            UserRepresentation userRep,
            String newEmail,
            boolean emailChanged
    ){}

    @Override
    public Mono<ProfileUpdateResult> updateUserProfile(String userId, UpdateUserOnboardingProfileRequest updateUserProfileRequest){
        return Mono.fromCallable(() -> {
                    // Fetch user from Keycloak
                    UserRepresentation userRep = adminManager.findUserByUserId(userId)
                            .orElseThrow(UserDoesNotExistException::new);

                    boolean keycloakNeedsUpdate = false;

                    keycloakNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.firstName(), userRep.getFirstName(), userRep::setFirstName);
                    keycloakNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.lastName(), userRep.getLastName(), userRep::setLastName);


                    String oldEmail = userRep.getEmail();
                    String proposedEmail = updateUserProfileRequest.email() != null ? updateUserProfileRequest.email().trim().toLowerCase() : null;
                    boolean emailChanged = proposedEmail != null && !proposedEmail.equalsIgnoreCase(oldEmail);

                    Optional<UserRepresentation> existingUserWithNewEmail = adminManager.findUserByEmail(proposedEmail);
                    if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(userId)) {
                        throw new UserExistsException("An account already exists for this email.");
                    }

                    // Perform update only if any changes detected
                    if (keycloakNeedsUpdate) {
                        adminManager.updateUser(userRep); // blocking Keycloak update
                    }

                    return new ProfileUpdateContext(userRep, proposedEmail, emailChanged);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ctx ->{
                    if(ctx.emailChanged){
                        // We do not update email in keycloak yet, We just trigger the "Confirmation" flow
                        return verificationService.createVerificationLink(
                                ctx.userRep.getEmail(),
                                VerificationType.APP_USER,
                                null,
                                ctx.newEmail
                        ).flatMap(verificationLink -> {
                            AppUserVerificationPayload payload = new AppUserVerificationPayload(
                                    ctx.userRep.getFirstName(), verificationLink, false);
                            return novuService.triggerEvent(NovuWorkflow.APP_USER_VERIFICATION, ctx.userRep.getId(), ctx.newEmail, payload);
                        })
                                .thenReturn(new ProfileUpdateResult(keycloakUserDtoMapper.mapToKeycloakUserDto(ctx.userRep), ctx.newEmail));
                    }
                    return Mono.just(new ProfileUpdateResult(keycloakUserDtoMapper.mapToKeycloakUserDto(ctx.userRep), null));
                });

    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return Mono.fromRunnable(() -> {
                    // Blocking lookup
                    adminManager.findUserByUserId(userId)
                            .orElseThrow(UserDoesNotExistException::new);

                    // Block deletion in Keycloak
                    adminManager.deleteUser(userId);

                    log.info("Successfully deleted user with ID: {}", userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<KeycloakUserDto> getUser(String userId) {
        return Mono.fromCallable(() ->
                        // Blocking lookup, then map to Response DTO
                        adminManager.findUserByUserId(userId)
                                .map(keycloakUserDtoMapper::mapToKeycloakUserDto)
                                .orElseThrow(UserDoesNotExistException::new)
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<KeycloakUserDto>> getAllUsers() {
        return Mono.fromCallable(() -> {
                    // Blocking call to list all users (no pagination)
                    List<UserRepresentation> userReps = adminManager.listAllUsers(); // Assuming there's a method to list all users

                    // Map the list of UserRepresentation to KeycloakUserDto DTOs
                    return userReps.stream()
                            .map(keycloakUserDtoMapper::mapToKeycloakUserDto)
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }


    @Override
    public Mono<Void> initiateForgotPassword(String email){
        // Start the reactive chain
        return Mono.fromCallable(()-> adminManager.findUserByEmail(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userOpt -> {
                    // If user exists, proceed with OTP generation and Novu
                    if(userOpt.isPresent()){
                        return otpWorker.generateAndSaveOtp(email, OtpPurpose.FORGOT_PASSWORD)
                                .flatMap(code -> novuService.triggerEvent(
                                        NovuWorkflow.FORGOT_PASSWORD_OTP,
                                        userOpt.get().getId(),
                                        email,
                                        new OtpPayload(code)
                                ))
                                .then();
                    }
                    // If user DOES NOT exist, log it internally but do nothing
                    log.warn("Forgot password requested for non-existent email: {}", email);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("Silent error during forgot password intitiation: ", e);
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<Void> completeForgotPassword(ForgotPasswordRequest forgotPasswordRequest){
        // Verify the otp (This deletes the OTP if successful, enforcing single-use
        return otpWorker.verifyOtp(forgotPasswordRequest.email(), forgotPasswordRequest.otpCode(), OtpPurpose.FORGOT_PASSWORD)
                .then(Mono.fromCallable(()-> {
                    // Find User by Email in Keycloak (Blocking call)
                    var userRep = adminManager.findUserByEmail(forgotPasswordRequest.email())
                            .orElseThrow(UserDoesNotExistException::new);

                    // Update password in Keycloak (Blocking call)
                    adminManager.resetPassword(userRep.getId(), forgotPasswordRequest.newPassword().trim());

                    return userRep.getId();
                }))
                // If they're in the lobby, move them to the next stage
                .flatMap(adminInvitationService::processPasswordResetSuccess)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(unused -> log.info("Password successfully reset via OTP for new user: {}", forgotPasswordRequest.email()))
                .then();
    }


    @Override
    public Mono<Void> resetPassword(String userId, ResetPasswordRequest resetPasswordRequest) {
        return Mono.fromCallable(() -> {
                    adminManager.findUserByUserId(userId) // Blocking lookup
                            .orElseThrow(UserDoesNotExistException::new);

                    adminManager.resetPassword(userId, resetPasswordRequest.newPassword().trim()); // Blocking reset

                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(unused -> adminInvitationService.processPasswordResetSuccess(userId))
                .then();
    }
}