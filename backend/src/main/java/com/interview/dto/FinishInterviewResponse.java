package com.interview.dto;

import com.interview.entity.InterviewRecord;
import lombok.Data;

@Data
public class FinishInterviewResponse {
    private Long recordId;
    private Long reportJobId;
    private String reportStatus;
    private InterviewRecord record;

    public static FinishInterviewResponse of(InterviewRecord record, Long reportJobId, String reportStatus) {
        FinishInterviewResponse response = new FinishInterviewResponse();
        response.setRecord(record);
        response.setRecordId(record == null ? null : record.getId());
        response.setReportJobId(reportJobId);
        response.setReportStatus(reportStatus);
        return response;
    }
}
