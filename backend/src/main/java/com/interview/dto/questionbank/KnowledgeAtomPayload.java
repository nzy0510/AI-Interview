package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeAtomPayload {
    private String id;
    private String subject;
    private String category;
    private String difficulty;
    private List<String> tags = new ArrayList<>();
    private Content content = new Content();
    private String sourceRef;

    @Data
    public static class Content {
        private String principles;
        private String pitfalls;
        private List<String> followUpPaths = new ArrayList<>();
    }
}
