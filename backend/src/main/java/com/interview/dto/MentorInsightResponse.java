package com.interview.dto;

import java.util.List;

/**
 * AI Mentor 洞察响应
 */
public class MentorInsightResponse {

    private Diagnosis diagnosis;
    private List<RiskAlert> riskAlerts;
    private List<ActionItem> actions;
    private KnowledgeCoverage knowledgeCoverage;
    private String generatedAt;

    public static class Diagnosis {
        private String overview;
        private List<String> strengths;
        private List<String> weaknesses;

        public String getOverview() { return overview; }
        public void setOverview(String overview) { this.overview = overview; }
        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }
        public List<String> getWeaknesses() { return weaknesses; }
        public void setWeaknesses(List<String> weaknesses) { this.weaknesses = weaknesses; }
    }

    public static class RiskAlert {
        private String type;
        private String message;
        private String severity; // info, warning, danger

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    public static class ActionItem {
        private String category;
        private String message;
        private Integer priority; // 1=immediate, 2=short-term, 3=weekly

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }

    public static class KnowledgeCoverage {
        private int totalCategories;
        private int coveredCategories;
        private double coveragePercent;
        private List<CategoryDetail> details;

        public static class CategoryDetail {
            private String category;
            private int covered;
            private int total;
            private double percent;

            public String getCategory() { return category; }
            public void setCategory(String category) { this.category = category; }
            public int getCovered() { return covered; }
            public void setCovered(int covered) { this.covered = covered; }
            public int getTotal() { return total; }
            public void setTotal(int total) { this.total = total; }
            public double getPercent() { return percent; }
            public void setPercent(double percent) { this.percent = percent; }
        }

        public int getTotalCategories() { return totalCategories; }
        public void setTotalCategories(int totalCategories) { this.totalCategories = totalCategories; }
        public int getCoveredCategories() { return coveredCategories; }
        public void setCoveredCategories(int coveredCategories) { this.coveredCategories = coveredCategories; }
        public double getCoveragePercent() { return coveragePercent; }
        public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }
        public List<CategoryDetail> getDetails() { return details; }
        public void setDetails(List<CategoryDetail> details) { this.details = details; }
    }

    public Diagnosis getDiagnosis() { return diagnosis; }
    public void setDiagnosis(Diagnosis diagnosis) { this.diagnosis = diagnosis; }
    public List<RiskAlert> getRiskAlerts() { return riskAlerts; }
    public void setRiskAlerts(List<RiskAlert> riskAlerts) { this.riskAlerts = riskAlerts; }
    public List<ActionItem> getActions() { return actions; }
    public void setActions(List<ActionItem> actions) { this.actions = actions; }
    public KnowledgeCoverage getKnowledgeCoverage() { return knowledgeCoverage; }
    public void setKnowledgeCoverage(KnowledgeCoverage knowledgeCoverage) { this.knowledgeCoverage = knowledgeCoverage; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}
