package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ForumCategoryTagRepository extends R2dbcRepository<ForumCategoryTagEntity, UUID> {
    Flux<ForumCategoryTagEntity> findByCategoryId(UUID categoryId);

    Flux<ForumCategoryTagEntity> findByTagName(String tagName);

    @Query("""
        SELECT * FROM category_tags
        WHERE category_id = :categoryId
        AND tag_name = :tagName
    """)
    Mono<ForumCategoryTagEntity> findByCategoryIdAndTagName(
            @Param("categoryId") UUID categoryId,
            @Param("tagName") String tagName
    );

    @Query("""
        DELETE FROM category_tags
        WHERE category_id = :categoryId
    """)
    Mono<Void> deleteByCategoryId(@Param("categoryId") UUID categoryId);


    @Query("""
        DELETE FROM category_tags
        WHERE category_id = :categoryId
        AND tag_name = :tagName
    """)
    Mono<Void> deleteByCategoryIdAndTagName(
            @Param("categoryId") UUID categoryId,
            @Param("tagName") String tagName
    );

    Mono<Boolean> existsByCategoryIdAndTagName(
            @Param("categoryId") UUID categoryId,
            @Param("tagName") String tagName
    );

    @Query("""
        SELECT COUNT(*) FROM category_tags
        WHERE category_id = :categoryId
    """)
    Mono<Long> countByCategoryId(
            @Param("categoryId") UUID categoryId
    );

    @Query("""
        SELECT DISTINCT ct.tag_name FROM category_tags ct
        INNER JOIN forum_categories c ON ct.category_id = c.id
        WHERE c.is_active = true
        ORDER BY ct.tag_name ASC
    """)
    Flux<String> findAllDistinctActiveTagNames();

}