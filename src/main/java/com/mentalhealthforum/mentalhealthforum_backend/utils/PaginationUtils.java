package com.mentalhealthforum.mentalhealthforum_backend.utils;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PaginationUtils {

    private static final Logger log = LoggerFactory.getLogger(PaginationUtils.class);
    /**
     * A generic helper method to handle pagination for any Flux of data.
     * It first counts the total elements and then applies 'skip' and 'take'
     * operations to retrieve the subset of data for the requested page.
     *
     * @param page The current page (0-indexed).
     * @param size The number of items per page.
     * @param dataFlux The Flux containing the data to paginate.
     * @param <T> The type of data in the Flux.
     * @return Mono<PaginatedResponse<T>> The paginated response containing the data and metadata.
     */
    public static <T>Mono<PaginatedResponse<T>> paginate(int page, int size, Flux<T> dataFlux){
        if (page < 0 || size <= 0) {

            log.error("Invalid pagination parameters: page={}, size={}", page, size);

            throw new InvalidPaginationException("Invalid pagination parameters: page >= 0 and size > 0 required.");
        }

        int firstResult = page * size;

        return dataFlux.count() // Fetch total number of users in the DB
                .flatMap(totalElements ->{
                    // Calculate total pages and check if we're on the last page
                    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
                    boolean isLastPage = page >= totalPages - 1;

                    // Return the data with pagination
                    return dataFlux
                            .skip(firstResult) // Skip to the page start
                            .take(size) // Limit to the page size
                            .collectList()  // Collect the results into a List
                            .map(content -> {
                                // Return PaginatedResponse with the mapped list
                                return new PaginatedResponse<>(content, page, size, totalElements, totalPages, isLastPage);
                            });
                });
    }
}
