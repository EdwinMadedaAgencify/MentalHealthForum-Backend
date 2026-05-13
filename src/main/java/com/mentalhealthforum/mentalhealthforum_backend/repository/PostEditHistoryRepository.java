package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.PostEditHistoryEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface PostEditHistoryRepository extends R2dbcRepository<PostEditHistoryEntity, UUID> {
    Flux<PostEditHistoryEntity> findByPostIdOrderByEditedAtDesc(UUID postId);
}
