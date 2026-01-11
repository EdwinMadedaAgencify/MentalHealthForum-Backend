package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.InviterDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.KeycloakUserDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.PendingAdminInviteDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitation;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.VerificationTokenRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AdminInvitationService;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import io.r2dbc.spi.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import java.util.UUID;


@Service
public class AdminInvitationServiceImpl implements AdminInvitationService {

    private static final Logger log = LoggerFactory.getLogger(AdminInvitationServiceImpl.class);

    private final KeycloakAdminManager adminManager;
    private final AdminInvitationRepository adminInvitationRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final DatabaseClient databaseClient;

    public AdminInvitationServiceImpl(
            KeycloakAdminManager adminManager,
            AdminInvitationRepository adminInvitationRepository,
            VerificationTokenRepository verificationTokenRepository,
            DatabaseClient databaseClient) {
        this.adminManager = adminManager;
        this.adminInvitationRepository = adminInvitationRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<AdminInvitation> createInvitation(KeycloakUserDto keycloakUserDto, String invitedById){
        List<String> groups = adminManager.getUserGroups(keycloakUserDto.userId());

        AdminInvitation adminInvitation = new AdminInvitation(
                keycloakUserDto.userId(),
                keycloakUserDto.email(),
                keycloakUserDto.username(),
                keycloakUserDto.firstName(),
                keycloakUserDto.lastName(),
                new HashSet<>(groups),
                keycloakUserDto.getCreatedInstant(),
                invitedById
        );
        return adminInvitationRepository.save(adminInvitation);
    }

    @Override
    public Mono<AdminInvitation> updateInvitation(KeycloakUserDto keycloakUserDto){
        List<String> groups = adminManager.getUserGroups(keycloakUserDto.userId());
        return adminInvitationRepository.findByKeycloakId(UUID.fromString(keycloakUserDto.userId()))
                .flatMap(existing -> {
                    // Update the fields to match the current Keycloak state
                    existing.setEmail(keycloakUserDto.email());
                    existing.setUsername(keycloakUserDto.username());
                    existing.setFirstName(keycloakUserDto.firstName());
                    existing.setLastName(keycloakUserDto.lastName());
                    existing.setIsEnabled(keycloakUserDto.enabled());
                    existing.setIsEmailVerified(keycloakUserDto.emailVerified());
                    existing.setUpdatedAt(Instant.now());
                    existing.setGroups(new HashSet<>(groups));

                    return adminInvitationRepository.save(existing);
                })
                // If they aren't in the lobby, we just return empty so the chain continues
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Mono<Void> processVerificationSuccess(String userId){
        return adminInvitationRepository.markEmailVerifiedAndAdvanceStage(UUID.fromString(userId))
                .doOnSuccess(count -> {
                    if(count > 0){
                        log.info("User {} successfully verified email and moved to PASSWORD_RESET stage.", userId);
                    }
                })
                .then();
    }

    @Override
    public Mono<Void> processPasswordResetSuccess(String userId){
        return adminInvitationRepository.invalidateOneTimePass(UUID.fromString(userId))
                .then(adminInvitationRepository.updateStage(UUID.fromString(userId), OnboardingStage.AWAITING_PROFILE_COMPLETION))
                .then();
    }


    @Override
    public Mono<Void> updateOnboardingStage(String userId, OnboardingStage onboardingStage){
        return adminInvitationRepository.updateStage(UUID.fromString(userId), onboardingStage)
                .doOnSuccess(v-> log.info("User {} moved to {}", userId, onboardingStage.name()))
                .then();
    }


    @Override
    public Mono<PaginatedResponse<PendingAdminInviteDto>> getPendingInvites(
            int page,
            int size,
            String[] groups,
            UUID invitedByUserId,
            String sortBy,
            String sortDirection,
            String search,
            OnboardingStage onboardingStage) {

        if (page < 0 || size <= 0) {
            log.error("Invalid pagination parameters: page={}, size={}", page, size);
            throw new InvalidPaginationException();
        }

        int offset = page * size;
        String normalizedSortBy = (sortBy == null)? "date_created": sortBy;
        String normalizedDir = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";

        String whereClause = """
                WHERE (:search IS NULL OR :search = ''
                    OR LOWER(ai.email) LIKE LOWER(CONCAT('%%', :search, '%%'))
                    OR LOWER(ai.username) LIKE LOWER(CONCAT('%%', :search, '%%'))
                    OR LOWER(ai.first_name) LIKE LOWER(CONCAT('%%', :search, '%%'))
                    OR LOWER(ai.last_name) LIKE LOWER(CONCAT('%%', :search, '%%')))
                AND (cardinality(CAST(:groups AS text[])) = 0 OR ai.groups && :groups)
                AND (:invitedByUserId IS NULL OR ai.invited_by = :invitedByUserId)
                AND (:onboardingStage IS NULL OR ai.current_stage = :onboardingStage::onboarding_stage_enum)
            """;

        String dataSql = """
            SELECT ai.*, au.keycloak_id as inviter_id, au.display_name as inviter_name
            FROM admin_invitations ai
            LEFT JOIN app_users au ON ai.invited_by = au.keycloak_id
            %s
            ORDER BY ai.%s %s
            LIMIT :limit OFFSET :offset
        """.formatted(whereClause, normalizedSortBy, normalizedDir); // Using .formatted() is much cleaner!

        String countSql = "SELECT COUNT(*) FROM admin_invitations ai %s".formatted(whereClause);

        // Execute Data Query
        Flux<PendingAdminInviteDto> contentFlux = databaseClient.sql(dataSql)
                .bind("search", search != null ? search.toLowerCase() : "")
                .bind("groups", groups != null ? groups : new String[0])
                .bind("limit", size)
                .bind("offset", offset)
                .bind("invitedByUserId", invitedByUserId != null
                        ? invitedByUserId
                        : Parameters.in(UUID.class))
                .bind("onboardingStage", onboardingStage != null
                        ? onboardingStage // Bind the Enum object, not .name()
                        : Parameters.in(OnboardingStage.class))
                .map((row, metadata) -> new PendingAdminInviteDto(
                        row.get("keycloak_id", UUID.class),
                        row.get("username", String.class),
                        row.get("first_name", String.class),
                        row.get("last_name", String.class),
                        row.get("email", String.class),
                        row.get("groups", String[].class),
                        Boolean.TRUE.equals(row.get("is_enabled", Boolean.class)),
                        Boolean.TRUE.equals(row.get("is_email_verified", Boolean.class)),
                        new InviterDto(
                                row.get("inviter_id", UUID.class),
                                row.get("inviter_name", String.class)
                        ),
                        row.get("date_created", Instant.class),
                        row.get("updated_at", Instant.class),
                        row.get("current_stage", OnboardingStage.class)
                ))
                .all();

        // Execute Count Query
        Mono<Long> totalCount = databaseClient.sql(countSql)
                .bind("search", search != null ? search.toLowerCase() : "")
                .bind("groups", groups != null ? groups : new String[0])
                .bind("invitedByUserId", invitedByUserId != null
                        ? invitedByUserId
                        : Parameters.in(UUID.class))
                .bind("onboardingStage", onboardingStage != null
                        ? onboardingStage // Bind the Enum object, not .name()
                        : Parameters.in(OnboardingStage.class))
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);

        return Mono.zip(contentFlux.collectList(), totalCount)
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));
    }



    @Override
    public Mono<Void> completeInvitation(UUID keycloakId){
        log.info("Completing invitation lifecycle for: {}. Removing from Lobby and clearing tokens.", keycloakId);

        // Fetch from our Lobby first (faster than hitting Keycloak)
        return adminInvitationRepository.findByKeycloakId(keycloakId)
                .flatMap(adminInvitation -> {
                    // Delete tokens based on the email from our Lobby record
                    return verificationTokenRepository.deleteByEmail(adminInvitation.getEmail())
                            .then(adminInvitationRepository.delete(adminInvitation));
                })
                .doOnSuccess(v -> log.info("Lobby record for {} successfully cleared.", keycloakId))
                .then();
    }
}


