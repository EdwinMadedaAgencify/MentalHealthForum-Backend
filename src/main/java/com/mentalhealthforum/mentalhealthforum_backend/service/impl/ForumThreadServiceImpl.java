package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.*;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumThreadService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
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
    private final ThreadTypeDefinitionRepository threadTypeDefinitionRepository;
    private final ThreadStatusDefinitionRepository threadStatusDefinitionRepository;

    public ForumThreadServiceImpl(
            TransactionalOperator transactionalOperator,
            AppUserRepository appUserRepository,
            ForumCategoryRepository forumCategoryRepository,
            ForumThreadRepository forumThreadRepository,
            ThreadTypeDefinitionRepository threadTypeDefinitionRepository,
            ThreadStatusDefinitionRepository threadStatusDefinitionRepository) {
        this.transactionalOperator = transactionalOperator;
        this.appUserRepository = appUserRepository;
        this.forumCategoryRepository = forumCategoryRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.threadTypeDefinitionRepository = threadTypeDefinitionRepository;
        this.threadStatusDefinitionRepository = threadStatusDefinitionRepository;
    }

    // ==================== USER ACTIONS ====================

    @Override
    public Mono<ThreadResponse> createThread(CreateThreadRequest request, ViewerContext viewerContext) {
        String userId = viewerContext.getUserId();
        List<String> normalizedTags = NormalizeUtils.normalizeTags(request.getTags());

        // 1. Validate user exists
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "User not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                )))
                // 2. Validate category exists and is active
                .flatMap(user -> forumCategoryRepository.findById(request.getCategoryId())
                        .switchIfEmpty(Mono.error(new ApiException(
                                "Category not found",
                                ErrorCode.RESOURCE_NOT_FOUND
                        )))
                        .flatMap(category -> {
                            if(!category.getIsActive()){
                                return Mono.error(new ApiException(
                                        "Cannot create thread in inactive category",
                                        ErrorCode.VALIDATION_FAILED
                                ));
                            }

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
                        }))
                .flatMap(this::mapToResponse)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ThreadResponse> getThread(UUID threadId, ViewerContext viewerContext){
        return findThread(threadId)
                .flatMap(thread -> {
                    return forumThreadRepository.incrementViewCount(threadId)
                            .then(mapToResponse(thread));
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
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {

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
                effectiveSearch,
                normalizedSortBy, normalizedSortDirection, size, offset);

        Mono<Long> totalCount = forumThreadRepository.countAllPaginated(
                categoryId,
                creatorId,
                effectiveThreadType,
                effectiveThreadStatus,
                isDeleted, isFeatured, hasContentWarning,
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
                    .flatMap(this::mapToResponse)
                    .collectList()
                    .map(response -> new PaginatedResponse<>(response, page, size, total));
        });
    }

    @Override
    public Mono<ThreadResponse> updateOwnThread(UUID threadId, UpdateOwnThreadRequest request, ViewerContext viewerContext){
        return performUserAction(
                threadId,
                viewerContext,
                "update thread",
                thread -> {
                    boolean updated = false;

                    // Creator can update these
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
                        return forumThreadRepository.save(thread).flatMap(this::mapToResponse);
                    }
                    return Mono.just(thread).flatMap(this::mapToResponse);
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
    public Mono<ThreadResponse> updateThreadStatus(UUID threadId, UpdateThreadStatusRequest request, ViewerContext viewerContext){
        // TODO: Store lockReason somewhere if needed (e.g., in thread_settings JSONB) later on will factor in where lockReason fits
        return performModeratorAction(
            threadId,
            viewerContext,
            "update thread status",
            thread -> {
                thread.setThreadStatus(request.toThreadStatus());
                thread.setUpdatedAt(Instant.now());
                return forumThreadRepository.save(thread).flatMap(this::mapToResponse);
            },
            thread -> !(request.toThreadStatus() == ThreadStatus.RESOLVED && thread.getThreadType() != ThreadType.QUESTION),
            "Only QUESTION threads can be marked as resolved",
                true
            );
    }

    @Override
    public Mono<ThreadResponse> updateThreadType(UUID threadId, UpdateThreadTypeRequest request, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "update thread type",
                thread -> {
                    ThreadType oldType = thread.getThreadType();
                    ThreadType newType = request.threadType();

                    // If changing from QUESTION to something else, clear resolution data
                    if(oldType == ThreadType.QUESTION && newType != ThreadType.QUESTION){
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

                    thread.setThreadType(request.threadType());
                    thread.setUpdatedAt(Instant.now());
                    return forumThreadRepository.save(thread).flatMap(this::mapToResponse);
                },
                null,
                null,
                true
        );
    }

    @Override
    public Mono<ThreadResponse> toggleSticky(UUID threadId, boolean sticky, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "update thread sticky",
                thread -> {
                    thread.setIsSticky(sticky);
                    thread.setUpdatedAt(Instant.now());
                    return forumThreadRepository.save(thread).flatMap(this::mapToResponse);
                },
                null,
                null,
                true
        );
    }

    @Override
    public Mono<ThreadResponse> toggleFeatured(UUID threadId, boolean featured, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "update thread featured",
                thread -> {
                    thread.setIsFeatured(featured);
                    thread.setUpdatedAt(Instant.now());
                    return forumThreadRepository.save(thread).flatMap(this::mapToResponse);
                },
                null,
                null,
                true
        );
    }

    @Override
    public Mono<Void> softDeleteThread(UUID threadId, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "soft delete thread",
                thread -> {
                    return  forumThreadRepository.softDeleteThread(threadId);
                },
                thread -> !thread.getIsDeleted(),
                "Thread is already deleted",
                true
        );
    }

    @Override
    public Mono<Void> restoreThread(UUID threadId, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "restore threads",
                thread -> forumThreadRepository.restoreThread(threadId),
                ForumThreadEntity::getIsDeleted,
                "Thread is not soft deleted",
                false
        );
    }

    @Override
    public Mono<Void> setBestAnswer(UUID threadId, UUID postId, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "set best answer",
                thread -> {
                    // Though I think in future it might be best to get resolved at from somewhere else
                    return forumThreadRepository.setBestAnswer(postId, threadId, UUID.fromString(viewerContext.getUserId()));
                },
                thread -> thread.getThreadType() == ThreadType.QUESTION,
                "Only QUESTION threads can have a best answer",
                true
        );
    }

    @Override
    public Mono<Void> clearBestAnswer(UUID threadId, ViewerContext viewerContext) {
        return performModeratorAction(
                threadId,
                viewerContext,
                "clear best answer",
                thread -> {
                    return forumThreadRepository.clearBestAnswer(threadId);
                },
                thread -> thread.getThreadType() == ThreadType.QUESTION,
                "Only QUESTION threads can have a best answer",
                true
        );
    }

    @Override
    public Mono<Void> permanentlyDeleteThread(UUID threadId, ViewerContext viewerContext) {
        // Only Admin can hard delete
        if (!viewerContext.isAdmin()) {
            return Mono.error(new ApiException("Only admins can permanently delete threads", ErrorCode.FORBIDDEN));
        }
        return findThread(threadId)
                .flatMap(thread -> {
                    // TODO: Also need to delete all posts? Posts have ON DELETE CASCADE
                    return forumThreadRepository.delete(thread);
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

    private <T> Mono<T> performModeratorAction(
            UUID threadId,
            ViewerContext viewerContext,
            String actionDescription,
            Function<ForumThreadEntity, Mono<T>> action,
            Predicate<ForumThreadEntity> validator,
            String validationErrorMessage,
            boolean requireActive
    ){
        if(!viewerContext.isModeratorOrAdmin()){
            return Mono.error(new ApiException("Only moderators can " + actionDescription, ErrorCode.FORBIDDEN));
        }
        return findThread(threadId)
                .flatMap(thread -> {
                    if(requireActive && thread.getIsDeleted()){
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


    private Mono<ThreadResponse> mapToResponse(ForumThreadEntity thread) {
        return Mono.zip(
                forumCategoryRepository.findById(thread.getCategoryId())
                        .switchIfEmpty(Mono.empty()),
                appUserRepository.findAppUserByKeycloakId(thread.getCreatorId().toString())
                        .switchIfEmpty(Mono.empty())
        ).map(tuple -> {
            ForumCategoryEntity category = tuple.getT1();
            AppUserEntity creator = tuple.getT2();

            return ThreadResponse.builder()
                    .id(thread.getId())
                    .categoryId(thread.getCategoryId())
                    .categoryName(category.getName())
                    .categorySlug(category.getSlug())
                    .title(thread.getTitle())
                    .creatorId(thread.getCreatorId())
                    .creatorDisplayName(creator.displayName())
                    .creatorAvatarUrl(creator.getAvatarUrl())
                    .threadType(thread.getThreadType())
                    .threadStatus(thread.getThreadStatus())
                    .contentWarningType(thread.getContentWarningType())
                    .tags(thread.getTags())
                    .isSticky(thread.getIsSticky())
                    .isFeatured(thread.getIsFeatured())
                    .postCount(thread.getPostCount())
                    .viewCount(thread.getViewCount())
                    .bestAnswerPostId(thread.getBestAnswerPostId())
                    .resolvedAt(thread.getResolvedAt())
                    .createdAt(thread.getCreatedAt())
                    .updatedAt(thread.getUpdatedAt())
                    .lastActivityAt(thread.getLastActivityAt())
                    .build();
        });
    }
}
