package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface AdminInvitationRepository extends R2dbcRepository<AdminInvitationEntity, UUID> {

    // Simple lookup to see if someone is already in the "Lobby"
    Mono<AdminInvitationEntity> findByKeycloakId(UUID keycloakId);

    Mono<AdminInvitationEntity> findByEmail(String email);

    @Modifying
    @Query("""
            UPDATE admin_invitations
            SET current_stage = 'AWAITING_PASSWORD_RESET',
                is_email_verified = TRUE,
                updated_at = NOW()
                WHERE keycloak_id = :keycloakId AND current_stage = 'AWAITING_VERIFICATION'
            """)
    Mono<Integer> markEmailVerifiedAndAdvanceStage(UUID keycloakId);


    @Modifying
    @Query("""
        UPDATE admin_invitations
        SET current_stage = :newStage,
            updated_at = NOW()
            WHERE keycloak_id = :keycloakId
    """)
    Mono<Integer> updateStage(UUID keycloakId, OnboardingStage newStage);

    @Modifying
    @Query("""
            UPDATE admin_invitations
            SET is_initial_login = FALSE
                WHERE keycloak_id = :keycloakId AND is_initial_login = TRUE
            """)
    Mono<Integer> invalidateOneTimePass(UUID keycloakId);

    // Clean up once the user is synced to app_users
    Mono<Void> deleteByKeycloakId(UUID keycloakId);
}
