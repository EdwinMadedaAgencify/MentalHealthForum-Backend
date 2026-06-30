package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.GlobalSearchResult;
import com.mentalhealthforum.mentalhealthforum_backend.service.impl.GlobalSearchServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class GlobalSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchRepository.class);

    private final DatabaseClient databaseClient;

    public GlobalSearchRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Slice<GlobalSearchResult>> executeLiveSearch(String rawQuery, String sortBy, Pageable pageable){

        // Safe, optimized outer execution sorting valuables
        String orderByClause;
        if("recent".equalsIgnoreCase(sortBy)){
            orderByClause = "last_activity_at DESC NULLS LAST, header ASC";
        }
        else { // relevance
            orderByClause = "search_score DESC, header ASC";
        }

        // Entirely consolidated core search query over active relational states
        String coreSearchUnionSql = """
                -- PERFORMANCE OPTIMIZATION: websearch_to_tsquery called ONCE via CTE
 
                WITH query_token AS (
                    SELECT websearch_to_tsquery('english', :query) AS tsquery
                )
                
                -- 1. THREADS
                SELECT
                    t.id AS entity_id,
                    'THREAD'::TEXT AS entity_type,
                    t.title AS header,
                    LEFT(t.title, 200)::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('english', coalesce(t.title, '')), 'A'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    t.last_activity_at AS last_activity_at
                FROM forum_threads t
                WHERE t.is_deleted = FALSE
                    AND to_tsvector('english', coalesce(t.title, '')) @@ (SELECT tsquery FROM query_token)
                
                UNION ALL
                
                -- 2. POSTS
                SELECT
                    p.id AS entity_id,
                    'POST'::TEXT AS entity_type,
                    CASE WHEN p.is_anonymous = TRUE
                        THEN 'Anonymous Reply'::VARCHAR
                        ELSE coalesce(u.display_name, 'forum_member'::VARCHAR)
                    END AS header,
                    LEFT(p.content, 200)::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('english', coalesce(p.content, '')), 'B'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    p.created_at AS last_activity_at
                FROM forum_posts p
                LEFT JOIN app_users u ON p.author_id = u.keycloak_id
                WHERE p.is_deleted = FALSE
                    AND p.flagged_for_review = FALSE
                    AND to_tsvector('english', coalesce(p.content, '')) @@ (SELECT tsquery FROM query_token)
                
                UNION ALL
                
                -- 3. CATEGORIES & CATEGORY TAGS
                
                SELECT
                    c.id AS entity_id,
                    'CATEGORY'::TEXT AS entity_type,
                    c.name AS header,
                    LEFT(c.description, 200)::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('english', coalesce(c.name, '')), 'A') ||
                        setweight(to_tsvector('english', coalesce(c.description, '')), 'B') ||
                        setweight(to_tsvector('english', coalesce(cat_tags.aggregated_tags, '')), 'A'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    c.created_at AS last_activity_at
                FROM forum_categories c
                LEFT JOIN (
                    SELECT cta.category_id, string_agg(ct.name, ' ') AS aggregated_tags
                    FROM  category_tag_assignments cta
                    JOIN category_tags ct ON cta.tag_id = ct.id
                    GROUP BY cta.category_id
                ) cat_tags ON c.id = cat_tags.category_id
                WHERE c.is_active = TRUE
                    AND (
                        to_tsvector('english', coalesce(c.name, '')) @@ (SELECT tsquery FROM query_token) OR
                        to_tsvector('english', coalesce(c.description, '')) @@ (SELECT tsquery FROM query_token) OR
                        to_tsvector('english', coalesce(cat_tags.aggregated_tags, '')) @@ (SELECT tsquery FROM query_token)
                    )
               
                UNION ALL
                -- 4. USER PROFILES (Display Name + Bio)
                SELECT
                    u.id AS entity_id,
                    'PROFILE'::TEXT AS entity_type,
                    u.display_name AS header,
                    LEFT(u.bio, 200)::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('english', coalesce(u.display_name, '')), 'A') ||
                        setweight(to_tsvector('english', coalesce(u.bio, '')), 'B'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    u.last_active_at AS last_activity_at
                FROM app_users u
                WHERE u.is_active = TRUE
                    AND u.account_deletion_requested_at IS NULL
                    AND (
                          to_tsvector('english', coalesce(u.display_name, '')) @@ (SELECT tsquery FROM query_token) OR
                          to_tsvector('english', coalesce(u.bio, '')) @@ (SELECT tsquery FROM query_token)
                    )
                """;

        // Pagination: Fetch one extra row to check if there's a next page
        int fetchSize = pageable.getPageSize() + 1;

        // The outer query is now crystal clear
        String dataSql = "WITH combined_search AS (" + coreSearchUnionSql + ")" +
                "SELECT entity_id, entity_type, header, body_preview, search_score, last_activity_at " +
                "FROM combined_search " +
                "ORDER BY " + orderByClause + " LIMIT :limit OFFSET :offset";

        // Log the search parameters
        log.debug("Search query: '{}', sortBy: '{}', page: {}, size: {}, offset: {}",
                rawQuery, sortBy, pageable.getPageNumber(), pageable.getPageSize(), pageable.getOffset());

        // Log the full SQL being executed
        log.debug("SQL: {}", dataSql);


        return databaseClient.sql(dataSql)
                .bind("query", rawQuery)
                .bind("limit", fetchSize)
                .bind("offset", (int) pageable.getOffset())
                .map((row, metadata) -> {
                    LocalDateTime dateTime = row.get("last_activity_at", LocalDateTime.class);
                    Instant instant = (dateTime != null)? dateTime.toInstant(ZoneOffset.UTC) : null;

                    return new GlobalSearchResult(
                            row.get("entity_id", UUID.class),
                            row.get("entity_type", String.class),
                            row.get("header", String.class),
                            row.get("body_preview", String.class),
                            row.get("search_score", Double.class),
                            instant
                    );
                })
                .all()
                .collectList()
                .map(results -> {
                    boolean hasNext = results.size() > pageable.getPageSize();
                    List<GlobalSearchResult> content = hasNext
                            ? results.subList(0, pageable.getPageSize())
                            : results;

                    return new SliceImpl<>(content, pageable, hasNext);
                });
    }
}
