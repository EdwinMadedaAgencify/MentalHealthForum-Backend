package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumThreadEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.PostEditHistoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.PostEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.PostEditHistoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.PostRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AnonymousNameGenerator;
import com.mentalhealthforum.mentalhealthforum_backend.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class PostServiceImpl implements PostService {

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final PostRepository postRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final AppUserRepository appUserRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final AnonymousNameGenerator anonymousNameGenerator;

    public PostServiceImpl(
            TransactionalOperator transactionalOperator,
            PostRepository postRepository,
            ForumThreadRepository forumThreadRepository,
            AppUserRepository appUserRepository,
            PostEditHistoryRepository postEditHistoryRepository,
            AnonymousNameGenerator anonymousNameGenerator) {
        this.transactionalOperator = transactionalOperator;
        this.postRepository = postRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.appUserRepository = appUserRepository;
        this.postEditHistoryRepository = postEditHistoryRepository;
        this.anonymousNameGenerator = anonymousNameGenerator;
    }

    // ==================== USER ACTIONS ====================

    @Override
    public Mono<PostResponse> createPost(CreatePostRequest request, ViewerContext viewerContext) {
        String userId = viewerContext.getUserId();

        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new ApiException("User not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(user -> findActiveThread(request.getThreadId()))
                .flatMap(thread -> validateParentPost(thread, request.getParentPostId()))
                .flatMap(thread -> createAndSavePost(request, userId, thread.getId()))
                .flatMap(this::mapToResponse)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<PostResponse> getPost(UUID postId, ViewerContext viewerContext) {
        return findPost(postId)
                .flatMap(this::mapToResponse);
    }

    @Override
    public Mono<PaginatedResponse<PostResponse>> getAllPosts(
            int page,
            int size,
            UUID threadId,
            UUID authorId,
            UUID parentPostId,
            PostType postType,
            Boolean hasContentWarning,
            Boolean isDeleted,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {

        UUID currentUserId = UUID.fromString(viewerContext.getUserId());

        // Handle deleted posts visibility
        if (isDeleted != null && isDeleted) {
            boolean canViewAllDeleted = ModerationAction.VIEW_DELETED_POSTS.isAllowedFor(viewerContext);

            if(!canViewAllDeleted){
                // Regular users can only see their own deleted posts
                authorId = currentUserId;
            }
        }

        return executeGetPostsQuery(page, size, threadId, authorId, parentPostId, postType, hasContentWarning, isDeleted,
                search, sortBy, sortDirection);
    }

    @Override
    public Mono<PostResponse> updateOwnPost(UUID postId, UpdatePostRequest request, ViewerContext viewerContext) {
        return performUserAction(
                postId,
                viewerContext,
                "update post",
                post -> {
                    // Capture previous state including content warnings
                    String previousContent = post.getContent();
                    Integer previousWordCount = post.getWordCount();
                    ContentWarningType previousContentWarningType = post.getContentWarningType();
                    String previousContentWarningCustomText = post.getContentWarningCustomText();

                    // Apply updates
                    post.setContent(request.getContent());
                    post.setContentWarningType(request.getContentWarningType());
                    post.setContentWarningCustomText(request.getContentWarningCustomText());
                    post.setIsEdited(true);
                    post.setEditReasonType(request.getEditReason());
                    post.setEditedByUserId(UUID.fromString(viewerContext.getUserId()));
                    post.setUpdatedAt(Instant.now());

                    String customEditReason = request.getEditReason() == EditReason.OTHER ? request.getEditReasonCustomText() : null;
                    post.setEditReasonCustomText(customEditReason);

                    // Save history with previous warning state
                    PostEditHistoryEntity history = PostEditHistoryEntity.builder()
                            .postId(post.getId())
                            .previousContent(previousContent)
                            .previousWordCount(previousWordCount)
                            .previousContentWarningType(previousContentWarningType)
                            .previousContentWarningCustomText(previousContentWarningCustomText)
                            .editedBy(UUID.fromString(viewerContext.getUserId()))
                            .editReasonType(request.getEditReason())
                            .editReasonCustomText(customEditReason)
                            .isModeratorEdit(false)
                            .build();

                    return postEditHistoryRepository.save(history)
                            .then(postRepository.save(post))
                            .flatMap(this::mapToResponse);
                },
                null,
                null
        );
    }

    @Override
    public Mono<Void> softDeleteOwnPost(UUID postId, ViewerContext viewerContext) {
        return performUserAction(
                postId,
                viewerContext,
                "soft delete post",
                post -> postRepository.softDeletePost(postId),
                null,
                null
        );
    }

    // ==================== MODERATOR ACTIONS ====================

    @Override
    public Mono<Void> softDeleteAnyPost(UUID postId, ViewerContext viewerContext) {
        return ModerationAction.POST_DELETED.checkPermission(viewerContext)
                .then(performModeratorAction(postId,
                        post -> postRepository.softDeletePost(postId),
                        List.of(new ValidationRule(PostEntity::getIsDeleted, "Cannot delete an already deleted post")),
                        true));
    }

    @Override
    public Mono<Void> restorePost(UUID postId, ViewerContext viewerContext) {
        return ModerationAction.POST_RESTORED.checkPermission(viewerContext)
                .then(performModeratorAction(postId,
                        post -> postRepository.restorePost(postId),
                        List.of(new ValidationRule(post -> !post.getIsDeleted(), "Cannot restore a post that is not deleted")),
                        false));
    }

    // ==================== ADMIN ACTIONS ====================

    @Override
    public Mono<PostResponse> addContentWarning(UUID postId, AddContentWarningRequest request, ViewerContext viewerContext){
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.POST_CONTENT_WARNING_ADDED.checkPermission(viewerContext)
                .then(performModeratorAction(postId,
                        post -> {
                            // Capture previous warning state
                            String previousContent = post.getContent();
                            Integer previousWordCount = post.getWordCount();
                            ContentWarningType previousContentWarningType = post.getContentWarningType();
                            String previousContentWarningCustomText = post.getContentWarningCustomText();

                            // Apply new warning
                            post.setContentWarningType(request.contentWarningType());
                            post.setContentWarningCustomText(request.contentWarningCustomText());
                            post.setIsEdited(true);
                            post.setEditedByUserId(moderatorId);
                            post.setUpdatedAt(Instant.now());

                            // Save history with previous warning state
                            PostEditHistoryEntity history = PostEditHistoryEntity.builder()
                                    .postId(post.getId())
                                    .previousContent(previousContent)
                                    .previousWordCount(previousWordCount)
                                    .previousContentWarningType(previousContentWarningType)
                                    .previousContentWarningCustomText(previousContentWarningCustomText)
                                    .editedBy(moderatorId)
                                    .editReasonType(EditReason.CONTENT_WARNING_ADDED)
                                    .isModeratorEdit(true)
                                    .build();

                            return postEditHistoryRepository.save(history)
                                    .then(postRepository.save(post))
                                    .flatMap(this::mapToResponse);
                        },
                        List.of(
                                new ValidationRule(
                                post -> post.getContentWarningType() == request.contentWarningType() &&
                                        Objects.equals(post.getContentWarningCustomText(), request.contentWarningCustomText()),
                                        "Post already has this content warning",
                                        ErrorCode.VALIDATION_FAILED
                                )
                        ),
                        true
                        ));



    }

    @Override
    public Mono<Void> permanentlyDeletePost(UUID postId, ViewerContext viewerContext) {
        return ModerationAction.POST_PERMANENTLY_DELETED.checkPermission(viewerContext)
                .then(findPost(postId))
                .flatMap(postRepository::delete)
                .as(transactionalOperator::transactional);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<PostEntity> findPost(UUID postId) {
        return postRepository.findById(postId)
                .switchIfEmpty(Mono.error(new ApiException("Post not found", ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Mono<PostEntity> findActivePost(UUID postId) {
        return findPost(postId)
                .flatMap(post -> {
                    if (post.getIsDeleted()) {
                        return Mono.error(new ApiException("Cannot modify a deleted post", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.just(post);
                });
    }

    private Mono<ForumThreadEntity> findThread(UUID threadId) {
        return forumThreadRepository.findById(threadId)
                .switchIfEmpty(Mono.error(new ApiException("Thread not found", ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Mono<ForumThreadEntity> findActiveThread(UUID threadId) {
        return findThread(threadId)
                .flatMap(thread -> {
                    if (thread.getIsDeleted()) {
                        return Mono.error(new ApiException("Cannot post in a deleted thread", ErrorCode.VALIDATION_FAILED));
                    }
                    if (thread.getThreadStatus() == ThreadStatus.CLOSED) {
                        return Mono.error(new ApiException("Cannot post in a closed thread", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.just(thread);
                });
    }

    private Mono<ForumThreadEntity> validateParentPost(ForumThreadEntity thread, UUID parentPostId) {
        if (parentPostId == null) {
            return Mono.just(thread);
        }
        return postRepository.existsByThreadIdAndId(thread.getId(), parentPostId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ApiException("Parent post does not belong to this thread", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.just(thread);
                });
    }

    private Mono<PostEntity> createAndSavePost(CreatePostRequest request, String userId, UUID threadId) {
        String anonymousIdentifier = null;
        if (request.getIsAnonymous()) {
            if (request.getCustomAnonymousName() != null && !request.getCustomAnonymousName().isBlank()) {
                anonymousIdentifier = anonymousNameGenerator.normalizeCustomAnonymousName(request.getCustomAnonymousName());
            } else {
                anonymousIdentifier = anonymousNameGenerator.generateAnonymousName(userId, threadId.toString());
            }
        }

        PostEntity post = PostEntity.builder()
                .threadId(request.getThreadId())
                .authorId(UUID.fromString(userId))
                .postType(request.getPostType())
                .parentPostId(request.getParentPostId())
                .content(request.getContent())
                .contentWarningType(request.getContentWarningType())
                .contentWarningCustomText(request.getContentWarningCustomText())
                .isAnonymous(request.getIsAnonymous())
                .anonymousIdentifier(anonymousIdentifier)
                .build();

        return postRepository.save(post);
    }

    private Mono<PaginatedResponse<PostResponse>> executeGetPostsQuery(
            int page,
            int size,
            UUID threadId,
            UUID authorId,
            UUID parentPostId,
            PostType postType,
            Boolean hasContentWarning,
            Boolean isDeleted,
            String search,
            String sortBy,
            String sortDirection
    ) {
        if (page < 0 || size <= 0) {
            log.error("Invalid pagination parameters: page = {}, size = {}", page, size);
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        String effectivePostType = (postType == null) ? null : postType.name();
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return postRepository.findPostsPaginated(
                        threadId, authorId, false, parentPostId, effectivePostType, hasContentWarning,
                        effectiveSearch, isDeleted, effectiveSortBy, effectiveSortDirection, size, offset)
                .flatMap(this::mapToResponse)
                .collectList()
                .zipWith(postRepository.countPostsWithFilters(
                        threadId, authorId, false, parentPostId, effectivePostType, hasContentWarning, effectiveSearch, isDeleted))
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));
    }

    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("created_at", "updated_at");
        if (sortBy == null || !allowedFields.contains(sortBy)) {
            return "created_at";
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String sortBy) {
        if (sortDirection != null) {
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return switch (sortBy) {
            case "updated_at" -> "DESC";
            default -> "ASC";
        };
    }

    private record ValidationRule(Predicate<PostEntity> condition, String errorMessage, ErrorCode errorCode) {
        public ValidationRule(Predicate<PostEntity> condition, String errorMessage) {
            this(condition, errorMessage, ErrorCode.VALIDATION_FAILED);
        }
    }

    private <T> Mono<T> performUserAction(
            UUID postId,
            ViewerContext viewerContext,
            String actionDescription,
            Function<PostEntity, Mono<T>> action,
            Predicate<PostEntity> validator,
            String validationErrorMessage
    ) {
        return findActivePost(postId)
                .flatMap(post -> {
                    if (!post.getAuthorId().toString().equals(viewerContext.getUserId())) {
                        return Mono.error(new ApiException("Only post author can " + actionDescription, ErrorCode.FORBIDDEN));
                    }
                    if (validator != null && !validator.test(post)) {
                        return Mono.error(new ApiException(validationErrorMessage, ErrorCode.VALIDATION_FAILED));
                    }
                    return action.apply(post);
                })
                .as(transactionalOperator::transactional);
    }

    private <T> Mono<T> performModeratorAction(
            UUID postId,
            Function<PostEntity, Mono<T>> action,
            List<ValidationRule> validators,
            boolean requireActive
    ) {
        Mono<PostEntity> postMono = requireActive ? findActivePost(postId) : findPost(postId);

        return postMono
                .flatMap(post -> {
                    for (ValidationRule rule : validators) {
                        if (rule.condition().test(post)) {
                            return Mono.error(new ApiException(rule.errorMessage(), rule.errorCode()));
                        }
                    }
                    return action.apply(post);
                })
                .as(transactionalOperator::transactional);
    }

    private Mono<PostResponse> mapToResponse(PostEntity post) {
        return appUserRepository.findAppUserByKeycloakId(post.getAuthorId().toString())
                .switchIfEmpty(Mono.empty())
                .map(author -> PostResponse.builder()
                        .id(post.getId())
                        .threadId(post.getThreadId())
                        .parentPostId(post.getParentPostId())
                        .authorId(post.getAuthorId())
                        .authorDisplayName(author.getDisplayName())
                        .authorAvatarUrl(author.getAvatarUrl())
                        .anonymousIdentifier(post.getAnonymousIdentifier())
                        .postType(post.getPostType())
                        .content(post.getContent())
                        .wordCount(post.getWordCount())
                        .contentWarningType(post.getContentWarningType())
                        .contentWarningCustomText(post.getContentWarningCustomText())
                        .isFlaggedForReview(false)
                        .isEdited(post.getIsEdited())
                        .editReason(post.getEditReasonType())
                        .editReasonCustomText(post.getEditReasonCustomText())
                        .isAnonymous(post.getIsAnonymous())
                        .isDeleted(post.getIsDeleted())
                        .reactionCount(post.getReactionCount())
                        .createdAt(post.getCreatedAt())
                        .updatedAt(post.getUpdatedAt())
                        .build());
    }
}