package com.interview.service.orchestration;

import java.util.Objects;

public record InterviewMessageSnapshot(String role, String content) {

    public InterviewMessageSnapshot {
        role = Objects.requireNonNullElse(role, "UNKNOWN");
        content = Objects.requireNonNullElse(content, "");
    }
}
