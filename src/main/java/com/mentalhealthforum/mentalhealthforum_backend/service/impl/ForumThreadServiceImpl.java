package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.AddContentWarningRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.*;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.BookmarkService;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumThreadService;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserModerationService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class ForumThreadServiceImpl implements ForumThreadService {

    private static final Logger log = LoggerFactory.getLogger(ForumThreadServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final AppUserRepository appUserRepository;
    private final ForumCategoryRepository forumCategoryRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final ThreadEditHistoryRepository threadEditHistoryRepository;
    private final PostRepository postRepository;
    private final ThreadTypeDefinitionRepository threadTypeDefinitionRepository;
    private final ThreadStatusDefinitionRepository threadStatusDefinitionRepository;
    private final BookmarkService bookmarkService;
    private final UserModerationService userModerationService;


    public ForumThreadServiceImpl(
            TransactionalOperator transactionalOperator,
            AppUserRepository appUserRepository,
            ForumCategoryRepository forumCategoryRepository,
            ForumThreadRepository forumThreadRepository,
            ThreadEditHistoryRepository threadEditHistoryRepository,
            PostRepository postRepository,
            ThreadTypeDefinitionRepository threadTypeDefinitionRepository,
            ThreadStatusDefinitionRepository threadStatusDefinitionRepository,
            BookmarkService bookmarkService,
            UserModerationService userModerationService) {
        this.transactionalOperator = transactionalOperator;
        this.appUserRepository = appUserRepository;
        this.forumCategoryRepository = forumCategoryRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.threadEditHistoryRepository = threadEditHistoryRepository;
        this.postRepository = postRepository;
        this.threadTypeDefinitionRepository = threadTypeDefinitionRepository;
        this.threadStatusDefinitionRepository = threadStatusDefinitionRepository;
        this.bookmarkService = bookmarkService;
        this.userModerationService = userModerationService;
    }

    // ==================== USER ACTIONS ====================

    @Override
    public Mono<ThreadResponse> createThread(CreateThreadRequest request, ViewerContext viewerContext) {
        String userId = viewerContext.getUserId();
        List<String> normalizedTags = NormalizeUtils.normalizeTags(request.getTags());


        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new ApiException("User not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(appUser -> userModerationService.requireNotMuted(appUser.getKeycloakId(), "create threads")
                        .then(validateCategoryActive(request.getCategoryId())))
                .flatMap(category -> createAndSaveThread(request, userId, normalizedTags))
                .flatMap(thread -> mapToResponse(thread, viewerContext))
                .as(transactionalOperator::transactional);
    }




    @Override
    public Mono<ThreadResponse> getThread(UUID threadId, ViewerContext viewerContext){
        return findThread(threadId)
                .flatMap(thread -> {
                    return forumThreadRepository.incrementViewCount(threadId)
                            .then(mapToResponse(thread, viewerContext));
                });
    }

    // ==================== THREAD LISTINGS WITH PAGINATION ====================

    @Override
    public Mono<PaginatedResponse<ThreadResponse>> getAllThreads(
            int page,
            int size,
            UUID categoryId,
            UUID creatorId,
            ThreadType threadType,
            ThreadStatus threadStatus,
            Boolean isDeleted,
            Boolean isFeatured,
            Boolean hasContentWarning,
            Boolean isBookmarked,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {

        UUID currentUserId = UUID.fromString(viewerContext.getUserId());

        if((isDeleted != null && isDeleted)){
            boolean canViewAllDeleted = ModerationAction.VIEW_DELETED_THREADS.isAllowedFor(viewerContext);
            // Silent: force filter to current user without error
            if(!canViewAllDeleted) {
                creatorId = currentUserId;
            }
        }

        if(page < 0 || size <= 0){
            log.error("Invalid pagination parameters: page={}, size={}", page, size);
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        String effectiveThreadType =  threadType != null? threadType.name() : null;
        String effectiveThreadStatus  = threadStatus != null? threadStatus.name() : null;
        String effectiveSearch = (search == null || search.trim().isEmpty())  ? null: search.trim();

        String normalizedSortBy = validateAndNormalizeSortBy(sortBy);
        String normalizedSortDirection = determineSortDirection(sortDirection, normalizedSortBy);

        Flux<ForumThreadEntity> theadsFlux = forumThreadRepository.findAllPaginated(
                categoryId,
                creatorId,
                effectiveThreadType,
                effectiveThreadStatus,
                isDeleted, isFeatured, hasContentWarning,
                currentUserId, isBookmarked,
                effectiveSearch,
                normalizedSortBy, normalizedSortDirection, size, offset);

        Mono<Long> totalCount = forumThreadRepository.countAllPaginated(
                categoryId,
                creatorId,
                effectiveThreadType,
                effectiveThreadStatus,
                isDeleted, isFeatured, hasContentWarning,
                currentUserId, isBookmarked,
                effectiveSearch);

        return Mono.zip(
                theadsFlux.collectList(),
                totalCount
        ).flatMap(tuple -> {
            List<ForumThreadEntity> threads = tuple.getT1();
            long total = tuple.getT2();

            if(threads.isEmpty()){
                return Mono.just(new PaginatedResponse<>(List.of(), page, size, total));
            }

            return Flux.fromIterable(threads)
                    .flatMap(thread -> mapToResponse(thread, viewerContext))
                    .collectList()
                    .map(response -> new PaginatedResponse<>(response, page, size, total));
        });
    }

    @Override
    public Mono<ThreadResponse> updateOwnThread(UUID threadId, UpdateOwnThreadRequest request, ViewerContext viewerContext){
        UUID creatorId = UUID.fromString(viewerContext.getUserId());
        return performUserAction(
                threadId,
                viewerContext,
                "update thread",
                thread -> {
                    boolean updated = false;

                    // Save edit history BEFORE changes
                    ThreadEditHistoryEntity history = ThreadEditHistoryEntity.builder()
                            .threadId(threadId)
                            .previousTitle(thread.getTitle())
                            .previousTags(thread.getTags())
                            .previousContentWarningType(thread.getContentWarningType())
                            .previousContentWarningCustomText(thread.getContentWarningCustomText())
                            .editedBy(creatorId)
                            .editReasonType(request.getEditReason())
                            .editReasonCustomText(request.getEditReasonCustomText())
                            .isModeratorEdit(false)
                            .build();

                    // Apply updates
                    if(request.getTitle() != null && !request.getTitle().equals(thread.getTitle())){
                        thread.setTitle(request.getTitle());
                        updated = true;
                    }

                    if(request.getContentWarningType() != null && !request.getContentWarningType().equals(thread.getContentWarningType())){
                        thread.setContentWarningType(request.getContentWarningType());
                        updated = true;
                    }

                    if(request.getContentWarningCustomText() != null && !request.getContentWarningCustomText().equals(thread.getContentWarningCustomText())){
                        thread.setContentWarningCustomText(request.getContentWarningCustomText());
                        updated = true;
                    }

                    if(request.getTags() != null && !request.getTags().equals(thread.getTags())){
                        List<String> normalizedTags = NormalizeUtils.normalizeTags(request.getTags());
                        thread.setTags(normalizedTags);
                        updated = true;
                    }

                    if(updated){
                        thread.setUpdatedAt(Instant.now());
                        return threadEditHistoryRepository.save(history)
                                        .then(forumThreadRepository.save(thread))
                                .flatMap(t -> mapToResponse(thread, viewerContext));
                    }
                    return Mono.just(thread).flatMap(t -> mapToResponse(thread, viewerContext));
                },
                null,
                null
        );
    }

    @Override
    public Mono<Void> softDeleteOwnThread(UUID threadId, ViewerContext viewerContext) {
        return performUserAction(
                threadId,
                viewerContext,
                "soft delete thread",
                thread -> {
                    return  forumThreadRepository.softDeleteThread(threadId);
                },
                null,
                null
        );
    }

    @Override
    public Mono<Void> setBestAnswerAsOriginalPoster(UUID threadId, UUID postId, ViewerContext viewerContext){
        return performUserAction(
                threadId,
                viewerContext,
                "set best answer",
                thread -> {
                    return forumThreadRepository.setBestAnswer(postId, threadId, UUID.fromString(viewerContext.getUserId()));
                },
                thread -> thread.getThreadType() == ThreadType.QUESTION,
                "Only QUESTION threads can have a best answer"
        );
    }

    // ==================== MODERATOR ACTIONS ====================

    @Override
    public Mono<ThreadResponse> archiveThread(UUID threadId, ViewerContext viewerContext){

        return ModerationAction.THREAD_ARCHIVED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.updateThreadStatus(threadId, ThreadStatus.ARCHIVED.name())
                                    .then(forumThreadRepository.clearLockMetadata(threadId))
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadStatus() == ThreadStatus.ARCHIVED,
                                        "Thread already archived",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> unArchiveThread(UUID threadId, ViewerContext viewerContext){

        return ModerationAction.THREAD_UNARCHIVED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.updateThreadStatus(threadId, ThreadStatus.OPEN.name())
                                    .then(forumThreadRepository.clearLockMetadata(threadId))
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadStatus() != ThreadStatus.ARCHIVED,
                                        "Thread is not archived",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> lockThread(UUID threadId, LockThreadRequest request, ViewerContext viewerContext){
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());
        return ModerationAction.THREAD_LOCKED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            Instant lockExpiry = request.durationHours() != null
                                    ? Instant.now().plus(request.durationHours(), ChronoUnit.HOURS)
                                    : null;

                            return forumThreadRepository.updateThreadStatus(threadId, ThreadStatus.CLOSED.name())
                                    .then(forumThreadRepository.updateLockReason(threadId, request.reason(), moderatorId))
                                    .then(forumThreadRepository.updateLockExpiry(threadId, lockExpiry))
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));

                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadStatus() == ThreadStatus.CLOSED,
                                        "Thread is already locked",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> unlockThread(UUID threadId, ViewerContext viewerContext){
        return ModerationAction.THREAD_UNLOCKED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.updateThreadStatus(threadId, ThreadStatus.OPEN.name())
                                    .then(forumThreadRepository.clearLockMetadata(threadId))
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadStatus() != ThreadStatus.CLOSED,
                                        "Thread is not locked",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> updateThreadType(UUID threadId, UpdateThreadTypeRequest request, ViewerContext viewerContext) {
        ThreadType newThreadType = request.threadType();

        return ModerationAction.THREAD_TYPE_CHANGED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            ThreadType oldThreadType = thread.getThreadType();

                            // If changing from QUESTION to something else, clear resolution data
                            if(oldThreadType == ThreadType.QUESTION && newThreadType != ThreadType.QUESTION){
                                thread.setBestAnswerPostId(null);
                                thread.setResolvedAt(null);
                                thread.setResolvedByUserId(null);

                                // Also change status from RESOLVED to OPEN if needed
                                if(thread.getThreadStatus() == ThreadStatus.RESOLVED){
                                    thread.setThreadStatus(ThreadStatus.OPEN);
                                }
                            }

                            // If changing TO QUESTION, no special action needed initially
                            // (status remains whatever it was, no best answer yet)

                            thread.setThreadType(newThreadType);
                            thread.setUpdatedAt(Instant.now());
                            return forumThreadRepository.save(thread).flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadType() == newThreadType,
                                        "Thread already has this type",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> toggleSticky(UUID threadId, boolean sticky, ViewerContext viewerContext) {
        return ModerationAction.THREAD_STICKY_TOGGLED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.updateStickyStatus(threadId, sticky)
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                            new ValidationRule(
                                thread -> thread.getIsSticky() == sticky,
                                    sticky? "Thread is already sticky" : "Thread is not sticky",
                                    ErrorCode.VALIDATION_FAILED
                            )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> toggleFeatured(UUID threadId, boolean featured, ViewerContext viewerContext) {
        ModerationAction moderationAction = featured? ModerationAction.THREAD_FEATURED : ModerationAction.THREAD_UNFEATURED;

        return moderationAction.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.updateFeaturedStatus(threadId, featured)
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getIsFeatured() == featured,
                                        featured? "Thread is already featured" : "Thread is not featured",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> moveThread(UUID threadId, UUID newCategoryId, ViewerContext viewerContext){
        return ModerationAction.THREAD_MOVED.checkPermission(viewerContext)
                .then(validateCategoryExists(newCategoryId))
                .then(performModeratorAction(threadId,
                        thread -> {
                            return forumThreadRepository.moveThread(threadId, newCategoryId)
                                            .then(findThread(threadId))
                                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getCategoryId().equals(newCategoryId),
                                        "Thread is already in this category",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<Void> softDeleteThread(UUID threadId, ViewerContext viewerContext) {
        return ModerationAction.THREAD_SOFT_DELETED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            return  forumThreadRepository.softDeleteThread(threadId);
                        },
                        List.of(
                            new ValidationRule(
                                    ForumThreadEntity::getIsDeleted,
                                    "Thread is already deleted",
                                    ErrorCode.VALIDATION_FAILED
                            )
                        ),
                        true
                ));
    }

    @Override
    public Mono<Void> restoreThread(UUID threadId, ViewerContext viewerContext) {
        return ModerationAction.THREAD_RESTORED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> forumThreadRepository.restoreThread(threadId),
                        List.of(
                            new ValidationRule(
                                thread -> !thread.getIsDeleted(),
                                "Thread is not soft deleted",
                                ErrorCode.VALIDATION_FAILED
                            )
                        ),
                        false
                ));
    }

    @Override
    public Mono<ThreadResponse> setBestAnswer(UUID threadId, UUID postId, ViewerContext viewerContext) {
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());
        return ModerationAction.THREAD_BEST_ANSWER_SET.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {
                            // Though I think in future it might be best to get resolved at from somewhere else
                            return forumThreadRepository.setBestAnswer(postId, threadId, moderatorId)
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadType() != ThreadType.QUESTION,
                                        "Only QUESTION threads can have a best answer",
                                        ErrorCode.VALIDATION_FAILED
                                ),
                                new ValidationRule(
                                        thread -> thread.getBestAnswerPostId() != null,
                                        "Thread already has a best answer. Clear it first",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> clearBestAnswer(UUID threadId, ViewerContext viewerContext) {
        return ModerationAction.THREAD_BEST_ANSWER_CLEARED.checkPermission(viewerContext)
                .then(performModeratorAction(
                        threadId,
                        thread -> {
                            return forumThreadRepository.clearBestAnswer(threadId)
                                    .then(findThread(threadId))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                                new ValidationRule(
                                        thread -> thread.getThreadType() != ThreadType.QUESTION,
                                        "Only QUESTION threads can have a best answer",
                                        ErrorCode.VALIDATION_FAILED
                                ),
                                new ValidationRule(
                                        thread -> thread.getBestAnswerPostId() == null,
                                        "Thread does not have a best answer to clear",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> addThreadContentWarning(UUID threadId, AddContentWarningRequest request, ViewerContext viewerContext){
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.THREAD_CONTENT_WARNING_ADDED.checkPermission(viewerContext)
                .then(performModeratorAction(threadId,
                        thread -> {

                            // Save edit history BEFORE changes
                            ThreadEditHistoryEntity history = ThreadEditHistoryEntity.builder()
                                    .threadId(threadId)
                                    .previousTitle(thread.getTitle())
                                    .previousTags(thread.getTags())
                                    .previousContentWarningType(thread.getContentWarningType())
                                    .previousContentWarningCustomText(thread.getContentWarningCustomText())
                                    .editedBy(moderatorId)
                                    .editReasonType(EditReason.CONTENT_WARNING_ADDED)
                                    .isModeratorEdit(true)
                                    .build();

                            // Apply updates
                            thread.setContentWarningType(request.contentWarningType());
                            thread.setContentWarningCustomText(request.contentWarningCustomText());
                            thread.setUpdatedAt(Instant.now());
                            return threadEditHistoryRepository.save(history)
                                    .then(forumThreadRepository.save(thread))
                                    .flatMap(t -> mapToResponse(thread, viewerContext));
                        },
                        List.of(
                            new ValidationRule(
                                thread -> thread.getContentWarningType() == request.contentWarningType() &&
                                        Objects.equals(thread.getContentWarningCustomText(), request.contentWarningCustomText()),
                                "Thread already has this content warning",
                                ErrorCode.VALIDATION_FAILED
                            )
                        ),
                        true
                ));
    }

    @Override
    public Mono<ThreadResponse> mergeThreads(MergeThreadRequest request, ViewerContext viewerContext){
        UUID sourceThreadId = request.sourceThreadId();
        UUID destinationThreadId = request.destinationThreadId();
        return ModerationAction.THREAD_MERGED.checkPermission(viewerContext)
                .then(validateThreadsExist(sourceThreadId, destinationThreadId))
                .then(validateThreadsNotIdentical(sourceThreadId, destinationThreadId))
                .then(validateThreadsNotDeleted(sourceThreadId, destinationThreadId))
                .then(Mono.zip(findThread(sourceThreadId), findThread(destinationThreadId)))
                .flatMap(tuple -> {

                    ForumThreadEntity sourceThread = tuple.getT1();
                    ForumThreadEntity destinationThread = tuple.getT2();

                    int sourcePostCount = sourceThread.getPostCount();

                    return postRepository.moveAllPostsToThread(sourceThreadId, destinationThreadId)
                            .then(forumThreadRepository.incrementPostCount(destinationThreadId, sourcePostCount))
                            .then(forumThreadRepository.updateLastActivity(destinationThreadId))
                            .then(forumThreadRepository.softDeleteThread(sourceThreadId))
                            .then(mapToResponse(destinationThread, viewerContext));
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ThreadResponse> splitThread(UUID sourceThreadId, SplitThreadRequest request, ViewerContext viewerContext){
        return ModerationAction.THREAD_SPLIT.checkPermission(viewerContext)
                .then(validatePostsExist(request.postIds()))
                .then(validatePostsBelongToThread(request.postIds(), sourceThreadId))
                .then(performModeratorAction(sourceThreadId,
                        sourceThread -> {
                            // Create new thread
                            ForumThreadEntity newThread = ForumThreadEntity.builder()
                                    .title(request.newThreadTitle())
                                    .creatorId(sourceThread.getCreatorId())
                                    .categoryId(sourceThread.getCategoryId())
                                    .threadType(sourceThread.getThreadType())
                                    .threadStatus(ThreadStatus.OPEN)
                                    .viewCount(0)
                                    .lastActivityAt(Instant.now())
                                    .build();

                            return forumThreadRepository.save(newThread)
                                    .flatMap(savedThread ->
                                            // Move posts to new thread
                                            postRepository.movePostsToThread(request.postIds(), savedThread.getId())
                                                    .then(forumThreadRepository.recalculatePostCount(savedThread.getId()))
                                                    .then(forumThreadRepository.decrementPostCount(sourceThreadId, request.postIds().size()))
                                                    .then(findThread(savedThread.getId()))
                                                    .flatMap(thread -> mapToResponse(thread, viewerContext))
                                    );

                        },
                        List.of(
                                new ValidationRule(
                                        ForumThreadEntity::getIsDeleted,
                                        "Cannot split from a deleted thread",
                                        ErrorCode.VALIDATION_FAILED
                                ),
                                new ValidationRule(
                                        thread -> request.postIds() == null || request.postIds().isEmpty(),
                                        "At least one post must be selected to split",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                ));
    }

    // ==================== ADMIN ACTIONS ====================

    @Override
    public Mono<Void> permanentlyDeleteThread(UUID threadId, ViewerContext viewerContext) {
        return ModerationAction.THREAD_PERMANENTLY_DELETED.checkPermission(viewerContext)
                .then(findThread(threadId))
                .flatMap(thread -> {
                    return threadEditHistoryRepository.deleteByThreadId(thread.getId())
                            .then(forumThreadRepository.delete(thread));
                })
                .as(transactionalOperator::transactional);
    }

    // ==================== REFERENCE DATA ====================

    @Override
    public Flux<ThreadTypeDefinitionEntity> getThreadTypes(){
        return threadTypeDefinitionRepository.findAllByOrderByDisplayNameASC();
    }

    @Override
    public Flux<ThreadStatusDefinitionEntity> getThreadStatuses(){
        return threadStatusDefinitionRepository.findAllByOrderByDisplayNameASC();
    }

    // ==================== PRIVATE HELPERS ====================

    private String validateAndNormalizeSortBy(String sortBy){
        Set<String> allowedFields = Set.of(
                "created_at", "last_activity_at", "post_count", "view_count", "title"
        );
        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "last_activity_at"; // Default to most recent activity
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String sortBy){
        if (sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection)? "DESC" : "ASC";
        }
        return switch (sortBy){
            case "created_at", "last_activity_at", "post_count", "view_count" -> "DESC";
            default -> "ASC"; // title
        };
    }



    private Mono<ForumThreadEntity> findThread(UUID threadId){
        return forumThreadRepository.findById(threadId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Thread not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                )));
    }

    private Mono<ForumThreadEntity> findActiveThread(UUID threadId) {
        return findThread(threadId)
                .flatMap(thread -> {
                    if (thread.getIsDeleted()) {
                        return Mono.error(new ApiException("Cannot modify a deleted thread", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.just(thread);
                });
    }

    private Mono<Void> validateCategoryExists(UUID categoryId) {
        return forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + categoryId + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> {
                    if(!category.getIsActive()){
                        return Mono.error(new ApiException(
                                "Cannot move thread to inactive category",
                                ErrorCode.VALIDATION_FAILED
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateThreadsExist(UUID sourceThreadId, UUID destinationThreadId) {
        return Mono.zip(
                forumThreadRepository.existsById(sourceThreadId),
                forumThreadRepository.existsById(destinationThreadId)
        ).flatMap(tuple -> {
            if (!tuple.getT1()) {
                return Mono.error(new ApiException("Source thread not found", ErrorCode.RESOURCE_NOT_FOUND));
            }
            if (!tuple.getT2()) {
                return Mono.error(new ApiException("Target thread not found", ErrorCode.RESOURCE_NOT_FOUND));
            }
            return Mono.empty();
        });
    }

    private Mono<Object> validateThreadsNotIdentical(UUID sourceThreadId, UUID destinationThreadId) {
        if(sourceThreadId.equals(destinationThreadId)){
            return Mono.error(new ApiException("Cannot merge a thread with itself", ErrorCode.VALIDATION_FAILED));
        }
        return Mono.empty();
    }

    private Mono<Void> validateThreadsNotDeleted(UUID sourceThreadId, UUID destinationThreadId) {
        return Mono.zip(
                forumThreadRepository.existsByIdAndIsDeletedFalse(sourceThreadId),
                forumThreadRepository.existsByIdAndIsDeletedFalse(destinationThreadId)
        ).flatMap(tuple -> {

            if (!tuple.getT1()) {
                return Mono.error(new ApiException("Source thread is deleted", ErrorCode.VALIDATION_FAILED));
            }
            if (!tuple.getT2()) {
                return Mono.error(new ApiException("Target thread is deleted", ErrorCode.VALIDATION_FAILED));
            }
            return Mono.empty();
        });
    }

    private Mono<Void> validatePostsExist(List<UUID> postIds) {
        return postRepository.countPostsInIds(postIds)
                .flatMap(count -> {
                    if(count != postIds.size()){
                        return Mono.error(new ApiException("One or more posts not found", ErrorCode.RESOURCE_NOT_FOUND));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validatePostsBelongToThread(List<UUID> postIds, UUID threadId){
        return postRepository.countPostsInIdsAndThread(postIds, threadId)
                .flatMap(count -> {
                    if(count != postIds.size()){
                        return Mono.error(new ApiException("One or more posts do not belong to the source thread", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<ForumCategoryEntity> validateCategoryActive(UUID categoryId){
        return  forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                )))
                .flatMap(category -> {
                    if (!category.getIsActive()) {
                        return Mono.error(new ApiException(
                                "Cannot create thread in inactive category",
                                ErrorCode.VALIDATION_FAILED
                        ));
                    }
                    return Mono.just(category);
                });
    }


    private Mono<ForumThreadEntity> createAndSaveThread(CreateThreadRequest request, String userId, List<String> normalizedTags) {
        // 3. Create thread entity
        ForumThreadEntity thread = ForumThreadEntity.builder()
                .title(request.getTitle())
                .creatorId(UUID.fromString(userId))
                .categoryId(request.getCategoryId())
                .threadType(request.getThreadType())
                .threadStatus(ThreadStatus.OPEN)
                .contentWarningType(request.getContentWarningType())
                .contentWarningCustomText(request.getContentWarningCustomText())
                .tags(normalizedTags)
                .viewCount(0)
                .lastActivityAt(Instant.now())
                .build();

        return forumThreadRepository.save(thread);
    }



    private <T> Mono<T> performUserAction(
            UUID threadId,
            ViewerContext viewerContext,
            String actionDescription,
            Function<ForumThreadEntity, Mono<T>> action,
            Predicate<ForumThreadEntity> validator,
            String validationErrorMessage
    ){
        return findThread(threadId)
                .flatMap(thread -> {

                    if(!thread.getCreatorId().toString().equals(viewerContext.getUserId())){
                        return Mono.error(new ApiException("Only thread creator can " + actionDescription, ErrorCode.FORBIDDEN));
                    }

                    if(thread.getIsDeleted()){
                        return Mono.error(new ApiException(
                                "Cannot modify a deleted thread",
                                ErrorCode.VALIDATION_FAILED
                        ));
                    }

                    if(validator != null && !validator.test(thread)){
                        return Mono.error(new ApiException(
                                validationErrorMessage,
                                ErrorCode.VALIDATION_FAILED
                        ));
                    }

                    return action.apply(thread);
                })
                .as(transactionalOperator::transactional);
    }

    private record ValidationRule(Predicate<ForumThreadEntity> condition, String errorMessage, ErrorCode errorCode){
        public ValidationRule(Predicate<ForumThreadEntity> condition, String errorMessage){
            this(condition, errorMessage, ErrorCode.VALIDATION_FAILED);
        }
    }

    private <T> Mono<T> performModeratorAction(
            UUID threadId,
            Function<ForumThreadEntity, Mono<T>> action,
            List<ValidationRule> validators,
            boolean requireActive
    ){
        // No generic role check here - let each action's checkPermission handle it
        Mono<ForumThreadEntity> threadMono = requireActive? findActiveThread(threadId) : findThread(threadId);

        return threadMono
                .flatMap(thread -> {
                    for(ValidationRule rule : validators){
                        if(rule.condition().test(thread)){
                            return Mono.error(new ApiException(rule.errorMessage, rule.errorCode));
                        }
                    }

                   return action.apply(thread);
                })
                .as(transactionalOperator::transactional);
    }


    private Mono<ThreadResponse> mapToResponse(ForumThreadEntity thread, ViewerContext viewerContext) {
        return Mono.zip(
                forumCategoryRepository.findById(thread.getCategoryId())
                        .switchIfEmpty(Mono.empty()),
                appUserRepository.findAppUserByKeycloakId(thread.getCreatorId().toString())
                        .switchIfEmpty(Mono.empty()),
                bookmarkService.isBookmarked(thread.getId(), viewerContext),
                bookmarkService.getBookmarkCountForThread(thread.getId())
        ).map(tuple -> {
            ForumCategoryEntity category = tuple.getT1();
            AppUserEntity creator = tuple.getT2();
            Boolean isBookmarked = tuple.getT3();
            long bookmarkCount = tuple.getT4();

            return ThreadResponse.builder()
                    .id(thread.getId())
                    .categoryId(thread.getCategoryId())
                    .categoryName(category.getName())
                    .categorySlug(category.getSlug())
                    .title(thread.getTitle())
                    .creatorId(thread.getCreatorId())
                    .creatorDisplayName(creator.getPublicIdentifier())
                    .creatorAvatarUrl(creator.getAvatarUrl())
                    .threadType(thread.getThreadType())
                    .threadStatus(thread.getThreadStatus())
                    .contentWarningType(thread.getContentWarningType())
                    .contentWarningCustomText(thread.getContentWarningCustomText())
                    .tags(thread.getTags())
                    .isSticky(thread.getIsSticky())
                    .isFeatured(thread.getIsFeatured())
                    .isBookmarked(isBookmarked)
                    .bookmarkCount((int)bookmarkCount)
                    .postCount(thread.getPostCount())
                    .viewCount(thread.getViewCount())
                    .bestAnswerPostId(thread.getBestAnswerPostId())
                    .resolvedAt(thread.getResolvedAt())
                    .resolvedByUserId(thread.getResolvedByUserId())

                    // lock metadata
                    .lockReason(thread.getLockReason())
                    .lockedBy(thread.getLockedBy())
                    .lockedAt(thread.getLockedAt())
                    .lockExpiresAt(thread.getLockExpiresAt())

                    // Edit metadata
                    .lastEditedAt(thread.getUpdatedAt())

                    // Timestamps
                    .createdAt(thread.getCreatedAt())
                    .updatedAt(thread.getUpdatedAt())
                    .lastActivityAt(thread.getLastActivityAt())
                    .build();
        });
    }
}
