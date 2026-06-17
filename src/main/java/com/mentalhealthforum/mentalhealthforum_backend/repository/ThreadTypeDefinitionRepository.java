package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadTypeDefinitionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ThreadTypeDefinitionRepository extends ReactiveCrudRepository<ThreadTypeDefinitionEntity, ThreadType> {

    @Query("SELECT * FROM thread_type_definitions ORDER BY display_name ASC")
    Flux<ThreadTypeDefinitionEntity> findAllByOrderByDisplayNameASC();

    Flux<ThreadTypeDefinitionEntity> findByThreadType(ThreadType threadType);
}
