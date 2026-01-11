package com.mentalhealthforum.mentalhealthforum_backend.enums;

import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.NovuPayload;
import lombok.Getter;

@Getter
public enum NovuWorkflow {
    SELF_REG_VERIFICATION("self-reg-verification", SelfRegPayload.class),
    ADMIN_ONBOARDING_INVITE("admin-onboarding-invite", AdminInvitePayload.class),
    FORGOT_PASSWORD_OTP("forgot-password-otp", OtpPayload .class),

    // Reflects that we are only refreshing the link, not the credentials
    RENEW_INVITATION_LINK("renew-invitation-link", InvitationLinkRenewalPayload.class),
    APP_USER_VERIFICATION("app-user-verification", AppUserVerificationPayload.class),

    MANUAL_TRIGGER_TEST("manual-trigger-test", ManualTriggerTestPayload.class);

    private final String workflowTrigger;
    private final Class<? extends NovuPayload> payloadClass;


    NovuWorkflow(String workflowTrigger, Class<? extends NovuPayload> payloadClass) {
        this.workflowTrigger = workflowTrigger;
        this.payloadClass = payloadClass;
    }
}
