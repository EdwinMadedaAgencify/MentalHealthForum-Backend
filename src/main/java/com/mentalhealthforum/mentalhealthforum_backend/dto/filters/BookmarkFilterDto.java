package com.mentalhealthforum.mentalhealthforum_backend.dto.filters;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BookmarkFilterDto {
    private List<FilterOption> creators;
    private List<FilterOption> categories;

}
