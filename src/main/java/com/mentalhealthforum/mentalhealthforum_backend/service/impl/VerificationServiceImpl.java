package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.FrontendProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.VerificationDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.AppUserVerificationPayload;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.InvitationLinkRenewalPayload;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.SelfRegPayload;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.NovuWorkflow;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidTokenException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.PendingRegistrationNotFoundException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.model.*;
import com.mentalhealthforum.mentalhealthforum_backend.repository.PendingUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.*;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType.INVITED;
import static com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType.SELF_REG;

@Service
public class VerificationServiceImpl implements VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationServiceImpl.class);

    private final KeycloakAdminManager adminManager;
    private final VerificationTokenService tokenWorker;
    private final PendingUserRepository pendingUserRepository;
    private final AdminInvitationRepository adminInvitationRepository;
    private final FrontendProperties frontendProperties;
    private final UserService userService;
    private final AppUserService appUserService;
    private final AdminInvitationService adminInvitationService;
    private final NovuService novuService;

    public VerificationServiceImpl(
            KeycloakAdminManager adminManager,
            VerificationTokenService tokenWorker,
            PendingUserRepository pendingUserRepository,
            AdminInvitationRepository adminInvitationRepository,
            FrontendProperties frontendProperties,
            @Lazy UserService userService,
            AppUserService appUserService,
            AdminInvitationService adminInvitationService, NovuService novuService) {
        this.adminManager = adminManager;
        this.tokenWorker = tokenWorker;
        this.pendingUserRepository = pendingUserRepository;
        this.adminInvitationRepository = adminInvitationRepository;
        this.frontendProperties = frontendProperties;
        this.userService = userService;
        this.appUserService = appUserService;
        this.adminInvitationService = adminInvitationService;
        this.novuService = novuService;
    }

    @Override
    public Mono<String> createVerificationLink(String email, VerificationType type, String groupPath, String newValue) {
        return tokenWorker.generateToken(email, type, groupPath, newValue)
                .map(verificationToken ->
                        String.format("%s/auth/verify?token=%s&email=%s",
                                frontendProperties.getBaseUrl(),
                                verificationToken.getToken(),
                                email));
    }

    @Override
    public Mono<Void> requestNewVerificationLink(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Check for Rate Limiting (reusing your existing log.error("Verification mapping failed: {}", e.getMessage());2-minute cooldown logic)
        return tokenWorker.checkRateLimit(normalizedEmail)
                // Try to find a Self-Reg user in staging
                .then(pendingUserRepository.findByEmail(normalizedEmail))
                .flatMap(this::triggerSelfRegRenewal)
                // If not in staging, check if they are an "Invited" user
                .switchIfEmpty(Mono.defer(()-> adminInvitationRepository.findByEmail(normalizedEmail)
                        .flatMap(this::triggerInviteRenewal)))
                .switchIfEmpty(Mono.defer(()-> handleAppUserEmailVerification(normalizedEmail) ))
                .then();
    }


    private Mono<Void> triggerInviteRenewal(AdminInvitation adminInvitation){
        String path =  GroupPath.MEMBERS_NEW.getPath();
        Set<String> groups = adminInvitation.getGroups();

        if(!groups.isEmpty()){
            Iterator<String> iterator = groups.iterator();
            path =  iterator.next();
        }

        return createVerificationLink(
                    adminInvitation.getEmail(),
                    INVITED,
                    path,
                    null
                )
                .flatMap(verificationLink -> {
                    InvitationLinkRenewalPayload payload = new InvitationLinkRenewalPayload(
                            adminInvitation.getFirstName(),
                            verificationLink
                    );

                    return novuService.triggerEvent(NovuWorkflow.RENEW_INVITATION_LINK, adminInvitation.getKeycloakId().toString(), adminInvitation.getEmail(), payload);
                })
                .doOnSuccess(v -> log.info("Successfully renewed invitation link for: {}", adminInvitation.getEmail()))
                .then();
    }

    private Mono<Void> handleAppUserEmailVerification(String email){
        return Mono.fromCallable(()-> adminManager.findUserByEmail(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userOpt -> {

                    if(userOpt.isEmpty()) return Mono.empty();


                    UserRepresentation user = userOpt.get();


                    // Case B: User is verified, but maybe they have a pending change
                    return tokenWorker.findTokenByEmailAndType(email, VerificationType.APP_USER)
                            .flatMap(verificationToken -> {
                                // Resend to the proposed email address
                                if(verificationToken.getNewValue() != null){
                                    return sendAppUserEmail(userOpt.get().getId(), user.getEmail(), verificationToken.getNewValue(), user.getFirstName());
                                }
                                return Mono.empty();
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Case A: User is unverified in Keycloak (Standard flow)
                                if(!Boolean.TRUE.equals(user.isEmailVerified())) {
                                    return sendAppUserEmail(userOpt.get().getId(), user.getEmail(), null, user.getFirstName());
                                }
                                return Mono.empty();
                            }));
                })

                .then();
    }

    private Mono<Void> sendAppUserEmail(String userId, String anchorEmail, String newValue, String firstname){
        return createVerificationLink(anchorEmail, VerificationType.APP_USER, null, newValue)
                .flatMap(verificationLink -> {
                    AppUserVerificationPayload payload = new AppUserVerificationPayload(
                            firstname,
                            verificationLink,
                            newValue == null
                    );
                    // Send newValue if it exists, otherwise anchorEmail
                    String targetRecipient = (newValue != null)? newValue: anchorEmail;
                    return novuService.triggerEvent(NovuWorkflow.APP_USER_VERIFICATION, userId, targetRecipient, payload);
                })
                .then();
    }


    private Mono<Void> triggerSelfRegRenewal(PendingUser pendingUser) {
        return createVerificationLink(
                    pendingUser.email(),
                    SELF_REG,
                    GroupPath.MEMBERS_NEW.getPath(),
                    null
                )
                .flatMap(verificationLink -> {
                    SelfRegPayload payload = new SelfRegPayload(
                            pendingUser.firstName(),
                            verificationLink
                    );

                    return novuService.triggerEvent(NovuWorkflow.SELF_REG_VERIFICATION, pendingUser.email(), pendingUser.email(), payload)
                            .thenReturn(pendingUser.email());
                })
                .doOnSuccess(v -> log.info("Successfully renewed staging link for: {}", pendingUser.email()))
                .then();
    }

    @Override
    public Mono<VerificationDto> processVerification(String token, String email) {
        return tokenWorker.findAndValidateToken(token, email)
                .flatMap(validToken -> switch (validToken.getType()){
                    case INVITED -> finalizeInvitedUser(validToken);
                    case SELF_REG -> finalizeSelfRegisteredUser(validToken);
                    case APP_USER -> finalizeExistingAppUser(validToken);
                });
    }

    private record FinalizeExistingAppUserContext(
            String userId,
            String username,
            String targetEmail
    ){}

    private Mono<VerificationDto> finalizeExistingAppUser(VerificationToken verificationToken) {
        return Mono.fromCallable(()->{
            UserRepresentation userRep = adminManager.findUserByEmail(verificationToken.getEmail())
                    .orElseThrow(()-> new UserDoesNotExistException("User not found for email change"));

            String targetEmail = verificationToken.getNewValue();

            if(targetEmail != null){
                // Case 1: Email change pathway
                // Update Keycloak: Change the email and mark as verified
                userRep.setEmail(verificationToken.getNewValue());
                userRep.setEmailVerified(true);
                adminManager.updateUser(userRep);
            }
            else {
                // Case 2: Standard verification (Account unlock)
                adminManager.verifyUserEmail(verificationToken.getEmail());
                targetEmail = verificationToken.getEmail();
            }
            return new FinalizeExistingAppUserContext(userRep.getId(), userRep.getUsername(), targetEmail);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ctx ->
                        appUserService.updateLocalEmail(ctx.userId, ctx.targetEmail)
                                .then(tokenWorker.removeToken(verificationToken.getId()))
                                .thenReturn(new VerificationDto(
                                        VerificationType.APP_USER,
                                        null, // No specific group path needed for existing users
                                        new VerificationDto.VerificationMetadata(
                                                ctx.userId,
                                                ctx.username,
                                                ctx.targetEmail
                                        )
                                ))
                );
    }

    private record FinalizeInvitedUserContext(
            String userId,
            String username
    ){}

    private Mono<VerificationDto> finalizeInvitedUser(VerificationToken verificationToken) {
        return Mono.fromCallable(()-> {
            // 1. Mark the user as verified in Keycloak (Blocking)
            adminManager.verifyUserEmail(verificationToken.getEmail());

            return adminManager.findUserByEmail(verificationToken.getEmail())
                    .map(userOpt -> new FinalizeInvitedUserContext(userOpt.getId(), userOpt.getUsername()))
                    .orElseThrow(UserDoesNotExistException::new);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ctx ->
                        adminInvitationService.processVerificationSuccess(ctx.userId)
                                .then( tokenWorker.removeToken(verificationToken.getId()))
                                .thenReturn(new VerificationDto(
                                        INVITED,
                                        verificationToken.getGroupPath(),
                                        new VerificationDto.VerificationMetadata(
                                                ctx.userId,
                                                ctx.username,
                                                verificationToken.getEmail()
                                        )
                                ))
                        );
    }

    private Mono<VerificationDto> finalizeSelfRegisteredUser(VerificationToken verificationToken) {
        return pendingUserRepository.findByEmail(verificationToken.getEmail())
                .switchIfEmpty(Mono.error(new PendingRegistrationNotFoundException()))
                .flatMap(pendingUser -> {
                    // Delegate the complex Keycloak work back to the UserService
                    return userService.createUserInKeycloak(pendingUser, GroupPath.MEMBERS_NEW.getPath())
                            .flatMap(keycloakUserDto -> appUserService.syncUserViaAdminClient(keycloakUserDto, null))
                            .flatMap(syncedUser -> pendingUserRepository.delete(pendingUser)
                                .then(tokenWorker.removeToken(verificationToken.getId()))
                                    .thenReturn(new VerificationDto(
                                            SELF_REG,
                                            verificationToken.getGroupPath(),
                                            new VerificationDto.VerificationMetadata(
                                                  syncedUser.getUserId().toString(),
                                                  pendingUser.username(),
                                                  verificationToken.getEmail()
                                            )
                                    ))
                            );
                });
    }
}
