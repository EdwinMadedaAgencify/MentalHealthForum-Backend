package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.GlobalSearchResult;
import com.mentalhealthforum.mentalhealthforum_backend.service.GlobalSearchService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public GlobalSearchController(
            GlobalSearchService globalSearchService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.globalSearchService = globalSearchService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @GetMapping
    public Mono<ResponseEntity<Slice<GlobalSearchResult>>> globalSearch(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String query,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return globalSearchService.searchRegistry(query, sortBy, page, size, viewerContext)
                .map(ResponseEntity::ok);
    }
}
