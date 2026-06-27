package com.mentalhealthforum.mentalhealthforum_backend.service;

/**
 * TODO: FUTURE RESOLUTION ORCHESTRATOR
 * This service will act as the single source of truth for handling moderation
 * side effects on the backend, preserving transactional integrity across different domains.
 * * It will depend on ContentReportRepository, UserModerationService, PostService, and ThreadService.
 * * Example Workflow:
 * public Mono<Void> resolveReportWithAction(UUID reportId, ModerationAction action, ViewerContext context) {
 * return reportRepo.findById(reportId)
 * .flatMap(report -> moderationService.execute(report, action, context))
 * .then(markReportAsResolved(reportId))
 * .as(transactionalOperator::transactional);
 * }
 */
public interface ReportWorkflowService {
    // Leave blank for now to maintain momentum on other features.
}
