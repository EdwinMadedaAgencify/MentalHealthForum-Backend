package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ReportSortField {
    SEVERITY("severity", "severity", "ASC"),
    REPORTED_AT("reported_at", "reported at", "DESC"),
    LAST_MODIFIED_AT("last_modified_at", "last modified at", "DESC");

    private final String value;
    private final String label;
    private final String defaultDirection;

    ReportSortField(String value, String label, String defaultDirection) {
        this.value = value;
        this.label = label;
        this.defaultDirection = defaultDirection;
    }

    public static ReportSortField fromString(String value) {
        if(value == null){
            return REPORTED_AT;  // Default to most recent
        }
        for(ReportSortField field : ReportSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  REPORTED_AT;
    }

    public String determineSortDirection(String sortDirection) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return this.defaultDirection;
    }

    private SortOption toSortOption(){
        return SortOption.builder()
                .value(this.value)
                .label(this.label)
                .defaultDirection(this.defaultDirection)
                .build();
    }

    public static List<SortOption> getOwnReportsSortOptions(){
        return List.of(SEVERITY.toSortOption(), REPORTED_AT.toSortOption());
    }

    public static List<SortOption> getAllReportsSortOptions(){
        return Arrays.stream(ReportSortField.values())
                .map(ReportSortField::toSortOption)
                .toList();
    }

}
