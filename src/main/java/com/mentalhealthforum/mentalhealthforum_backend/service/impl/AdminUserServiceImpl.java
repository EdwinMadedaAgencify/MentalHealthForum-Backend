package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RequiredAction;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UsernameGenerationException;
import com.mentalhealthforum.mentalhealthforum_backend.service.AdminUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils.normalizeUnicode;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    private final KeycloakAdminManager adminManager;

    public AdminUserServiceImpl(KeycloakAdminManager adminManager) {
        this.adminManager = adminManager;
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
     * @param request Admin user creation request containing user details and group assignment
     * @return Mono containing the AdminCreateUserResponse with user ID, temporary password, and invitation details
     */
    @Override
    public Mono<AdminCreateUserResponse> createUserAsAdmin(AdminCreateUserRequest request) {
        return Mono.fromCallable(()-> {
                    // 1. Normalize and validate inputs
                    String email = request.email().trim().toLowerCase();
                    String firstName = request.firstName().trim();
                    String lastName = request.lastName().trim();

                    // 2. Validate uniqueness (same as self-registration)
                    if(adminManager.findUserByEmail(email).isPresent()){
                        throw new UserExistsException("An account already exists for this email.");
                    }

                    // 3. Determine username (auto-generate if not provided)
                    String username = request.username() != null?
                            request.username().trim() :
                            generateUsername(firstName, lastName);

                    if(adminManager.findUserByUsername(username).isPresent()){
                        // If auto-generated username exists, add random suffix
                        username = username + "." + ThreadLocalRandom.current().nextInt(100, 199);
                    }

                    // 3. Auto-generate strong password
                    String temporaryPassword = generateTemporaryPassword();

                    var passwordCred = adminManager.createPasswordCredential(temporaryPassword, true);

                    // 5. Create user with PENDING ACTIONS
                    UserRepresentation user = new UserRepresentation();
                    user.setEnabled(true);
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setCredentials(List.of(passwordCred));

                    // CRITICAL DIFFERENCE: Set pending actions
                    user.setEmailVerified(false); // Admin-created users need to verify
                    user.setRequiredActions(determineRequiredActions(request.group()));

                    // 6. Create user in Keycloak
                    String userId = adminManager.createUser(user);

                    // 7. Assign to specified group (not default /members/new)
                    adminManager.assignUserToGroup(userId, request.group());

                    // 8. Store temporary password for response (never in DB)
                    // We'll return it in the response DTO

                    // 9. Return response with temporary password and placeholder invitation link
                    // Note: Email sending and invitation link generation will be added later with Novu
                    return new AdminCreateUserResponse(
                            userId,
                            username,
                            temporaryPassword,
                            "invitation-link-placeholder", // In real impl: actual link
                            false // Email not sent yet (Novu integration later
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String generateUsername(String firstName, String lastName) {
        // Normalize Unicode characters (é → e, ç → c)
        String normalizedFirstName = normalizeUnicode(firstName.toLowerCase());
        String normalizedLastName = normalizeUnicode(lastName.toLowerCase());

        String base = normalizedFirstName + "." + normalizedLastName;

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
        actions.add(RequiredAction.UPDATE_PASSWORD);

        if(group == GroupPath.MODERATORS_PROFESSIONAL){
            actions.add(RequiredAction.UPDATE_PROFILE);
        }

        if(group == GroupPath.ADMINISTRATORS){
            actions.add(RequiredAction.CONFIGURE_TOTP);
        }

        return actions.stream().map(Enum::name).toList();
    }
}
