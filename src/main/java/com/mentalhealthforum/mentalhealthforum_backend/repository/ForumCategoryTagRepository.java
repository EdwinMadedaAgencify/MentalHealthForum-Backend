package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ForumCategoryTagRepository extends R2dbcRepository<ForumCategoryTagEntity, UUID> {
    Flux<ForumCategoryTagEntity> findByCategoryId(UUID categoryId);

    Flux<ForumCategoryTagEntity> findByTagName(String TagName);

    @Query("""
        SELECT * FROM category_tags
        WHERE category_id = :categoryId
        AND tagName = :tagName
    """)
    Mono<ForumCategoryTagEntity> findByCategoryIdAndTagName(UUID categoryId, String tagName);

    @Query("""
        DELETE FROM category_tags
        WHERE category_id = :categoryId
    """)
    Mono<Void> deleteByCategoryId(UUID categoryId);


    @Query("""
        DELETE FROM category_tags
        WHERE category_id = :categoryId
        AND tagName = :tagName
    """)
    Mono<Void> deleteByCategoryIdAndTagName(UUID categoryId, String tagName);

    Mono<Boolean> existsByCategoryIdAndTagName(UUID categoryId, String tagName);

    @Query("""
        SELECT COUNT(*) FROM category_tags
        WHERE category_id = :categoryId
    """)
    Mono<Long> countByCategoryId(UUID categoryId);

    @Query("""
        SELECT DISTINCT ct.tagName FROM category_tags ct
        INNER JOIN forum_categories c ON ct.category_id = c.id
        WHERE c.is_active = true
        ORDER BY ct.tagName ASC
    """)
    Flux<String> findAllDistinctActiveTagNames();

}