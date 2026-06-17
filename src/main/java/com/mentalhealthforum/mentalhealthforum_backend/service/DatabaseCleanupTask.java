package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
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
    private final CategoryService categoryService;
    private final ThreadRepository threadRepository;
    private final UserConnectRepository userConnectRepository;

    public DatabaseCleanupTask(
            OtpCredentialRepository otpCredentialRepository,
            VerificationTokenRepository verificationTokenRepository,
            PendingUserRepository pendingUserRepository,
            CategoryService categoryService,
            ThreadRepository threadRepository,
            UserConnectRepository userConnectRepository) {
        this.otpCredentialRepository = otpCredentialRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.pendingUserRepository = pendingUserRepository;
        this.categoryService = categoryService;
        this.threadRepository = threadRepository;
        this.userConnectRepository = userConnectRepository;
    }

    // Runs at 3:00 AM every day
    @Scheduled( cron = "0 0 3 * * *")
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

    // Run at 2 AM daily
    @Scheduled(cron = "0 0 2 * * ?")
    public void purgeOldInactiveCategories(){
        log.info("Starting scheduled purge of old inactive categories");
        categoryService.purgeOldInactiveCategoriesInternal(90)
                .subscribe(
                        v -> log.info("Scheduled purge completed"),
                        e -> log.error("Scheduled purge failed: {}", e.getMessage())
                );
    }

    // Runs Every minute
    @Scheduled(cron = "0 * * * * *")
    public void unlockExpiredThreads(){
        threadRepository.unlockExpiredThreads()
                .subscribe(
                        count -> log.info("Unlock {} expired threads", count)
                );
    }

    // Run at 2 AM daily
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupDeclinedConnections(){
        log.info("Starting cleanup of declined connections older than 30 days");
        userConnectRepository.deleteDeclinedOlderThan(30)
                .subscribe(
                    count -> log.info("Cleaned up {} declined connections", count),
                        error -> log.error("Error cleaning up declined connections: {}", error.getMessage())
                );
    }

}
