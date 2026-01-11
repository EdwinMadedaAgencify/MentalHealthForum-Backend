package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.repository.OtpCredentialRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.PendingUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.VerificationTokenRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DatabaseCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCleanupTask.class);

    private final OtpCredentialRepository otpCredentialRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PendingUserRepository pendingUserRepository;

    public DatabaseCleanupTask(
            OtpCredentialRepository otpCredentialRepository,
            VerificationTokenRepository verificationTokenRepository,
            PendingUserRepository pendingUserRepository) {
        this.otpCredentialRepository = otpCredentialRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.pendingUserRepository = pendingUserRepository;
    }

    // Runs at 3:00 AM every day
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredOtps() {
        log.info("Cron: Initiating scheduled cleanup of expired OTP credentials...");

        otpCredentialRepository.deleteAllExpired(Instant.now())
                .doOnSuccess(count -> {
                    if (count > 0) { log.info("Cron Success: Cleanup complete. Removed {} stale records from 'otp_credentials'.", count);}
                    else { log.debug("Cron Success: No expired OTP records found to clean up.");}
                })
                .doOnError(e -> log.error("Cron Failure: Failed to execute OTP cleanup task. Reason: {}", e.getMessage()))
                .block();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupStaleRegistrations(){
        log.info("Cron: Initiating scheduled cleanup of stale pending registrations...");

        // Delete expired tokens first
        verificationTokenRepository.deleteAllExpired(Instant.now())
                .doOnSuccess(count -> {
                    if(count > 0){ log.info("Cron success: Removed {} expired verification tokens.", count);}
                    else { log.debug("Cron Success: No expired verification tokens found to clean up.");}
                })
                .doOnError(e -> log.debug("Cron Failure: Failed to executed expired verification token cleanup task: Reason: {}", e.getMessage()))
                .block();

        // 2. Delete pending users who have NO associated token
        // (This implies their 24h window closed)
        pendingUserRepository.deleteOrphanedPendingUsers()
                .doOnSuccess(count -> {
                    if(count > 0){ log.info("Cron Success: Removed {} stale pending user records.", count);}
                    else {log.info("Cron Success: No expired pending user records found to clean up.");}
                })
                .doOnError(e -> log.debug("Cron Failure: Failed to executed pending users token cleanup task: Reason: {}", e.getMessage()))
                .block();

    }
}
