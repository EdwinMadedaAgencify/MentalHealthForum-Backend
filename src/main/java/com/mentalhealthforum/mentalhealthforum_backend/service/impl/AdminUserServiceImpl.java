package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.AdminInvitePayload;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.*;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.*;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.ChangeUtils.setIfChanged;
import static com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils.normalizeUnicode;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    private final KeycloakAdminManager adminManager;
    private final KeycloakUserDtoMapper keycloakUserDtoMapper;
    private final VerificationService verificationService;
    private final NovuService novuService;
    private final AdminInvitationService adminInvitationService;
    private final AdminInvitationRepository adminInvitationRepository;


    // 1. Define the internal "Assembly Line" package
    private record AdminUserContext(
            String userId,
            String username,
            String email,
            String firstName,
            String tempPassword,
            String groupName,
            boolean sendInvitationEmail
    ){}

    public AdminUserServiceImpl(
            KeycloakAdminManager adminManager,
            KeycloakUserDtoMapper keycloakUserDtoMapper,
            VerificationService verificationService,
            NovuService novuService,
            AdminInvitationService adminInvitationService, AdminInvitationRepository adminInvitationRepository) {
        this.adminManager = adminManager;
        this.keycloakUserDtoMapper = keycloakUserDtoMapper;
        this.verificationService = verificationService;
        this.novuService = novuService;
        this.adminInvitationService = adminInvitationService;
        this.adminInvitationRepository = adminInvitationRepository;
    }

    /**
     * Creates a new user as an administrator.
     * -
     * Different from self-registration:
     * - Auto-generates password (admin doesn't know user's password)
     * - Sets pending actions for onboarding (email verification, password reset, etc.)
     * - Allows explicit group assignment (not default /members/new)
     * - Optionally sends invitation email
     *
     * @param request       Admin user creation request containing user details and group assignment
     * @param viewerContext Viewer Context
     * @return Mono containing the AdminCreateUserResponse with user ID, temporary password, and invitation details
     */
    @Override
    public Mono<AdminCreateUserResponse> createUserAsAdmin(
            AdminCreateUserRequest request,
            ViewerContext viewerContext) {
        return Mono.fromCallable(()-> {
                    // 1. Normalize and validate inputs
                    String email = request.email().trim().toLowerCase();
                    String firstName = request.firstName().trim();
                    String lastName = request.lastName().trim();

                    String username = request.username() != null?
                            request.username().trim() :
                            generateUsername(firstName, lastName);

                    String temporaryPassword = generateTemporaryPassword();

                    // Validate uniqueness (same as self-registration)
                    if(adminManager.findUserByEmail(email).isPresent()){
                        throw new UserExistsException("An account already exists for this email.");
                    }

                    if(adminManager.findUserByUsername(username).isPresent()){
                        // If auto-generated username exists, add random suffix
                        username = "%s.%d".formatted(username, ThreadLocalRandom.current().nextInt(100, 199));
                    }

                    var passwordCred = adminManager.createPasswordCredential(temporaryPassword);

                    // Create user with PENDING ACTIONS
                    UserRepresentation userRep = new UserRepresentation();
                    userRep.setEnabled(true);
                    userRep.setUsername(username);
                    userRep.setEmail(email);
                    userRep.setFirstName(firstName);
                    userRep.setLastName(lastName);
                    userRep.setCredentials(List.of(passwordCred));

                    // Set pending actions
                    userRep.setEmailVerified(false); // Admin-created users need to verify
                    userRep.setRequiredActions(determineRequiredActions(request.group()));


                    // Create user in Keycloak
                    String userId = adminManager.createUser(userRep);

                    // Assign to specified group (not default /members/new)
                    adminManager.assignUserToGroup(userId, request.group());
                    adminManager.markAsSyncedLocally(userId, false);
                    adminManager.assignInternalRole(userId, InternalRole.ONBOARDING);

                    // Wrap in the Record instead of a Map
                    return new AdminUserContext(
                            userId,
                            username,
                            email,
                            firstName,
                            temporaryPassword,
                            getFriendlyGroupName(request.group().getPath()),
                            request.sendInvitationEmail()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                // --- SURGICAL INJECTION START ---
                .flatMap(ctx -> {
                    return Mono.fromCallable(() -> adminManager.findUserByUserId(ctx.userId)
                            .map(keycloakUserDtoMapper::mapToKeycloakUserDto)
                            .orElseThrow(() -> new UserDoesNotExistException("Failed to retrieve created user")))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(keycloakUserDto ->
                                    adminInvitationService.createInvitation(keycloakUserDto, viewerContext.getUserId()))
                            .thenReturn(ctx); // Return the context to keep the assembly line moving
                })
                .flatMap(this::handleInvitationFlow);
    }

    @Override
    public Mono<AdminCreateUserResponse> reissueAdminInvitation(String userId, ReissueInvitationRequest request){
        String email = request.email().trim().toLowerCase();

        // 5. Generate a new verification invitation
        return Mono.fromCallable(()-> {
            // 1. Find user in Keycloak (Blocking call)
                    UserRepresentation user = adminManager.findUserByUserId(userId)
                            .orElseThrow(UserDoesNotExistException::new);

            // 2. Security Check: Don't resend if they are already synced (active)
            if(adminManager.isSyncedLocally(userId)){
                throw new UserAlreadyActiveException();
            }

            // 3. Security Check: Don't resend if they are already verified
            if(Boolean.TRUE.equals(user.isEmailVerified())){
                throw new InvitationAlreadyVerifiedException();
            }

            if(!user.getEmail().equalsIgnoreCase(email)){
                user.setEmail(email);
                adminManager.updateUser(user);
            }

            // 4. Generate a fresh temporary password
            String newTempPassword = generateTemporaryPassword();
            var passwordCred = adminManager.createPasswordCredential(newTempPassword);

            // 5. Update Keycloak (Resetting the temp password)
            adminManager.resetPassword(user.getId(), newTempPassword);

            // Note: We'd need to know the groupPath. If we don't store it,
            // we can fetch the user's current groups from Keycloak.
            // Fetch group path while still in the blocking thread pool
            if(request.group() != null){
                adminManager.assignUserToGroup(userId, request.group());
            }

            String groupPath = adminManager.getUserPrimaryGroupPath(user.getId());

            // Wrap in the Record instead of a Map
            return new AdminUserContext(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    newTempPassword,
                    getFriendlyGroupName(groupPath),
                    request.sendInvitationEmail()
            );

        }).subscribeOn(Schedulers.boundedElastic())
                // --- SURGICAL INJECTION START ---
                .flatMap(ctx ->{
                    return Mono.fromCallable(() -> adminManager.findUserByUserId(ctx.userId)
                                    .map(keycloakUserDtoMapper::mapToKeycloakUserDto)
                                    .orElseThrow(() -> new UserDoesNotExistException("Failed to retrieve user")))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(adminInvitationService::updateInvitation)
                            .thenReturn(ctx); // Return the co
                })
                .flatMap(ctx -> {
                    return adminInvitationRepository.findByKeycloakId(UUID.fromString(ctx.userId))
                            .flatMap(adminInvitation -> {
                                // RESET the flags so the new temp password actually works
                                adminInvitation.setIsInitialLogin(true);
                                adminInvitation.setCurrentStage(OnboardingStage.AWAITING_VERIFICATION);
                                return adminInvitationRepository.save(adminInvitation);
                            })
                            .thenReturn(ctx);
                })
                .flatMap(this::handleInvitationFlow);
    }

    @Override
    public Mono<KeycloakUserDto> updateUserAsAdmin(String userId, AdminUpdateUserRequest request) {
        return Mono.fromCallable(()-> {
            // Fetch user from Keycloak
            UserRepresentation userRep = adminManager.findUserByUserId(userId)
                            .orElseThrow(UserDoesNotExistException::new);

            boolean isSynced = adminManager.isSyncedLocally(userId);

            if (!isSynced) {
                throw new UserNotReadyException(
                        "Cannot update profile: User has not completed onboarding. Use 'Reissue Invite' to manage pending users.");
            }

            boolean isEnabledChanged = false;
            boolean isGroupChanged = false;

            isEnabledChanged = setIfChanged(request.isEnabled(), userRep.isEnabled(), userRep::setEnabled);
            if(isEnabledChanged && request.isEnabled() != null){
                log.info("Updating enabled status for user {} to {}", userId, request.isEnabled());
            }

            List<String> currentGroups = adminManager.getUserGroups(userId);
            String targetGroupPath = request.group().getPath();

            isGroupChanged = currentGroups.isEmpty() || !currentGroups.contains(targetGroupPath);

            if(isGroupChanged){
                adminManager.assignUserToGroup(userId, request.group());
                log.info("Updated group for user {} to {}", userId, targetGroupPath);
            }

            if(isEnabledChanged || isGroupChanged){
                adminManager.updateUser(userRep);
                log.info("Successfully updated profile for user ID: {}", userId);
            } else {
                log.debug("No profile changes detected for user ID: {}", userId);
            }

           return keycloakUserDtoMapper.mapToKeycloakUserDto(userRep);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> revokeInvitation(String userId) {
        return Mono.fromCallable(()-> {
            // 1. Fetch the latest state from Keycloak (Blocking call)
            UserRepresentation userRep = adminManager.findUserByUserId(userId)
                    .orElseThrow(UserDoesNotExistException::new);

            // 2. SAFETY GATE: Enforce the "Lobby Only" rule
            // If they are verified OR Keycloak says they are already synced, they are no longer an "Invitation"

            // Security Check: Don't revoke if they are already synced (active)
            if(adminManager.isSyncedLocally(userId)){
                throw new UserAlreadyActiveException();
            }

            // Security Check: Don't revoke if they are already verified
            if(Boolean.TRUE.equals(userRep.isEmailVerified())){
                throw new InvitationAlreadyVerifiedException();
            }

            adminManager.deleteUser(userId);
            return userId;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(id -> adminInvitationService.completeInvitation(UUID.fromString(id)));
    }

    /**
     * Reusable logic for the "Invitation" phase of the mpango.
     * This handles the verification link and the conditional Novu trigger.
     */
    private Mono<AdminCreateUserResponse> handleInvitationFlow(AdminUserContext ctx){
        return verificationService.createVerificationLink(ctx.email, VerificationType.INVITED, ctx.groupName, null)
                .flatMap(invitationLink -> {

                    // Logic: Only send if the admin requested it
                    if(ctx.sendInvitationEmail){
                        // 6. Trigger Novu with the NEW temp password

                        AdminInvitePayload payload = new AdminInvitePayload(
                                ctx.firstName,
                                ctx.tempPassword,
                                invitationLink,
                                ctx.groupName
                        );

                        return novuService.triggerEvent(NovuWorkflow.ADMIN_ONBOARDING_INVITE, ctx.userId, ctx.email, payload)
                                .map(sentStatus ->  new AdminCreateUserResponse(
                                        ctx.userId,
                                        ctx.username,
                                        ctx.tempPassword,
                                        invitationLink,
                                        sentStatus
                                ));
                    }

                    return Mono.just(new AdminCreateUserResponse(
                            ctx.userId,
                            ctx.username,
                            ctx.tempPassword,
                            invitationLink,
                            false
                    ));
                });
    }



    private String getFriendlyGroupName(String groupPath){
        GroupPath group = GroupPath.fromPath(groupPath);
        return (group != null) ? group.getDisplayName(): "our community";
    }

    private String generateUsername(String firstName, String lastName) {
        // Normalize Unicode characters (é → e, ç → c)
        String normalizedFirstName = normalizeUnicode(firstName.toLowerCase());
        String normalizedLastName = normalizeUnicode(lastName.toLowerCase());

        String base = "%s.%s".formatted(normalizedFirstName, normalizedLastName);

        // Remove any remaining invalid characters
        String cleaned = base.replaceAll("[^a-z0-9._]", "");

        // Remove leading/trailing dots and underscores
        cleaned = cleaned.replaceAll("^[._]+|[._]+$", "");

        // Replace multiple consecutive dots/underscores with single
        cleaned = cleaned.replaceAll("[._]{2,}", ".");

        // Ensure valid length
        if(cleaned.isEmpty()){
            throw new UsernameGenerationException(
                    String.format(
                            "Could not generate a valid username from names: '%s %s'. " +
                            "Please provide a username manually.",
                            firstName, lastName
                    )
            );
        }
        else if(cleaned.length() < 3){
            throw new UsernameGenerationException(
                    String.format(
                            "Could not generate username '%s' is too short (minimum 3 characters). " +
                            "Please provide a username manually.",
                            cleaned
                    )
            );
        }

        return cleaned.substring(0, Math.min(cleaned.length(), 30));
    }

    private String generateTemporaryPassword() {
        final int PASSWORD_LENGTH = 12;

        // Character sets that avoid ambiguous characters (no 0, O, I, l, 1, etc.)
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // No I, O
        String lower = "abcdefghijkmnopqrstuvwxyz"; // No l
        String digits = "23456789"; // No 0, 1
        String special = "!@#$%^&*";

        SecureRandom random = new SecureRandom();

        // Build password ensuring at least one of each required type
        StringBuilder password = new StringBuilder();
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // fill the rest
        String allChars = upper + lower + digits + special;
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // shuffle
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    private List<String> determineRequiredActions(GroupPath group){
        List<RequiredAction> actions = new ArrayList<>();

        actions.add(RequiredAction.VERIFY_EMAIL);

//        if(group == GroupPath.ADMINISTRATORS){
//            actions.add(RequiredAction.CONFIGURE_TOTP);
//        }

        return actions.stream().map(Enum::name).toList();
    }


}
