package com.interview.dto.report;

import com.interview.entity.InterviewReportItem;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InterviewReportItemResponse {
    private Long id;
    private Integer itemIndex;
    private String phase;
    private String question;
    private String userAnswer;
    private BigDecimal score;
    private String referenceAnswer;
    private String improvementSuggestion;
    private String answerSource;
    private String matchedAtomSnapshotJson;

    public static InterviewReportItemResponse from(InterviewReportItem item) {
        InterviewReportItemResponse response = new InterviewReportItemResponse();
        response.setId(item.getId());
        response.setItemIndex(item.getItemIndex());
        response.setPhase(item.getPhase());
        response.setQuestion(item.getQuestion());
        response.setUserAnswer(item.getUserAnswer());
        response.setScore(item.getScore());
        response.setReferenceAnswer(item.getReferenceAnswer());
        response.setImprovementSuggestion(item.getImprovementSuggestion());
        response.setAnswerSource(item.getAnswerSource());
        response.setMatchedAtomSnapshotJson(item.getMatchedAtomSnapshotJson());
        return response;
    }
}
