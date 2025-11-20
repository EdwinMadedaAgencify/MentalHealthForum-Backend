package com.mentalhealthforum.mentalhealthforum_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PaginatedResponse<T>{
    private  List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean  isLastPage;

    public PaginatedResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean isLastPage) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.isLastPage = isLastPage;
    }
}
