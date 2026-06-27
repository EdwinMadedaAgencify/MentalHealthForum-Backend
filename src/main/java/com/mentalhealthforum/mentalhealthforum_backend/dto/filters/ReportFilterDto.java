package com.mentalhealthforum.mentalhealthforum_backend.dto.filters;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReportFilterDto {
    private List<FilterOption> reporters;
    private List<FilterOption> reportedUsers;
    private List<FilterOption> threads;
    private List<FilterOption> assignedTo;
    private List<FilterOption> reviewers;
    private List<FilterOption> reportStatus;
}
