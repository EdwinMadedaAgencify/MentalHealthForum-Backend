package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.BookmarkService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/users/me/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public BookmarkController(
            BookmarkService bookmarkService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.bookmarkService = bookmarkService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<BookmarkResponse>>> addBookmark(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookmarkRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return bookmarkService.addBookmark(request, viewerContext)
                .map(bookmark -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new StandardSuccessResponse<>("Thread bookmarked successfully", bookmark)));
    }

    @DeleteMapping("/{threadId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> removeBookmark(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return bookmarkService.removeBookmark(threadId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Bookmark removed successfully"))));
    }

    @GetMapping("/{threadId}/check")
    public Mono<ResponseEntity<StandardSuccessResponse<Boolean>>> isBookmarked(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return bookmarkService.isBookmarked(threadId, viewerContext)
                .map(isBookmarked -> ResponseEntity.ok(new StandardSuccessResponse<>("Bookmark status retrieved", isBookmarked)));
    }

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<BookmarkResponse>>>> getMyBookmarks(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Parameter(name = "page", description = "Page number (0-indexed)", example = "0") int page,
            @RequestParam(defaultValue = "20") @Parameter(name = "size", description = "Number of items per page", example = "20") int size,
            @RequestParam(defaultValue = "") @Parameter(name = "search", description = "Search in thread title or bookmark notes", example = "anxiety") String search,
            @RequestParam(defaultValue = "bookmarked_at", name = "sort_by")
            @Parameter(name = "sort_by", description = "Sort field: title, bookmarked_at, last_activity_at, post_count", example = "bookmarked_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction")
            @Parameter(name = "sort_direction", description = "Sort direction: asc (ascending) or desc (descending)", example = "desc") String sortDirection
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return bookmarkService.getMyBookmarks(page, size, search, sortBy, sortDirection, viewerContext)
                .map(bookmarks -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Bookmarks retrieved successfully", bookmarks)));
    }

    @GetMapping("/count")
    public Mono<ResponseEntity<StandardSuccessResponse<Long>>> getBookmarkCountByUserId(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return bookmarkService.getBookmarkCountByUserId(viewerContext)
                .map(count -> ResponseEntity.ok(new StandardSuccessResponse<>("Bookmark count retrieved", count)));
    }

}
