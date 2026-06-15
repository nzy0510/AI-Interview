package com.interview.dto.report;

import com.interview.entity.InterviewReport;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewReportResponse {
    private Long id;
    private Long recordId;
    private Long jobId;
    private String status;
    private Integer overallScore;
    private String summary;
    private String abilityJson;
    private String recommendationJson;
    private String errorMessage;
    private LocalDateTime generatedAt;
    private List<InterviewReportItemResponse> items;

    public static InterviewReportResponse from(InterviewReport report, List<InterviewReportItemResponse> items) {
        InterviewReportResponse response = new InterviewReportResponse();
        response.setId(report.getId());
        response.setRecordId(report.getRecordId());
        response.setJobId(report.getJobId());
        response.setStatus(report.getStatus());
        response.setOverallScore(report.getOverallScore());
        response.setSummary(report.getSummary());
        response.setAbilityJson(report.getAbilityJson());
        response.setRecommendationJson(report.getRecommendationJson());
        response.setErrorMessage(report.getErrorMessage());
        response.setGeneratedAt(report.getGeneratedAt());
        response.setItems(items);
        return response;
    }
}
