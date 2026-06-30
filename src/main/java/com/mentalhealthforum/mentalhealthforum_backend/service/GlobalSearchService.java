package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.GlobalSearchResult;
import org.springframework.data.domain.Slice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GlobalSearchService {
    Mono<Slice<GlobalSearchResult>> searchRegistry(String query, String sortBy, int page, int size, ViewerContext viewerContext);
}
