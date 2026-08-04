package com.interview.dto;

public record QuestionBankCapabilitiesResponse(boolean userMaintenanceEnabled,
                                               boolean admin,
                                               boolean canAccessWorkspace) {
}
