package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingStatusResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OnboardingServiceImpl implements  OnboardingService{

    public final AppUserRepository appUserRepository;
    public final AdminInvitationRepository adminInvitationRepository;
    public final JwtClaimsExtractor jwtClaimsExtractor;

    public OnboardingServiceImpl(
            AppUserService appUserService, AppUserRepository appUserRepository,
            AdminInvitationRepository adminInvitationRepository,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.appUserRepository = appUserRepository;
        this.adminInvitationRepository = adminInvitationRepository;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @Override
    public Mono<OnboardingStatusResponse> getOnboardingStatus(ViewerContext viewerContext) {
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return adminInvitationRepository.findByKeycloakId(userId)
                .map(adminInvitation -> new OnboardingStatusResponse(
                        adminInvitation.getCurrentStage(),
                        false, // Always false in the lobby
                        false,
                        List.of(new OnboardingPolicy.Violation("account", "Account initialization in progress.")),
                        List.of(),
                        getLobbyMessage(adminInvitation.getCurrentStage()),
                        Map.of()
                ))
                .switchIfEmpty(Mono.defer(()-> appUserRepository.findAppUserByKeycloakId(String.valueOf(userId))
                        .map(appUser->{
                            OnboardingPolicy.Result result = viewerContext.checkOnboardingPolicy(appUser);

                            return new OnboardingStatusResponse(
                                    OnboardingStage.AWAITING_PROFILE_COMPLETION,
                                    true, // isSynced: Account is live in the main DB
                                    result.isSatisfied(),
                                    result.violations(),
                                    result.requirements(),
                                    result.isSatisfied()? "Welcome back!": "Role-based profile updates required.",
                                    Map.of()
                            );
                        }))
                );

    }

    private String getLobbyMessage(OnboardingStage onboardingStage) {
        return switch (onboardingStage){
            case AWAITING_VERIFICATION -> "Please verify your email address";
            case AWAITING_PASSWORD_RESET -> "Please set your permanent password";
            case AWAITING_PROFILE_COMPLETION -> "Initial setup complete. Please update your profile details.";
        };
    }
}
