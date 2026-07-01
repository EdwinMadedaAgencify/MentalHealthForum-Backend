package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.GlobalSearchResult;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.repository.GlobalSearchRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.GlobalSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchServiceImpl.class);

    private final GlobalSearchRepository globalSearchRepository;

    public GlobalSearchServiceImpl(GlobalSearchRepository globalSearchRepository) {
        this.globalSearchRepository = globalSearchRepository;
    }

    @Override
    public Mono<Slice<GlobalSearchResult>> searchRegistry(String query, String sortBy, int page, int size, ViewerContext viewerContext){
        if(query == null || query.isBlank()){
            return Mono.empty();
        }

        if (page < 0 || size <= 0) {
            log.error("Invalid pagination parameters: page={}, size={}", page, size);
            throw new InvalidPaginationException();
        }

        // Validate pagination inputs to prevent memory allocation attacks
       int validatedSize = Math.clamp(size, 1, 50);

        return globalSearchRepository.executeLiveSearch(query.trim(), sortBy, PageRequest.of(page, validatedSize), viewerContext);
    }
}
