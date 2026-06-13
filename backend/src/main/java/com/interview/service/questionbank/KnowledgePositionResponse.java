package com.interview.service.questionbank;

public record KnowledgePositionResponse(Long id,
                                        String scope,
                                        Long ownerUserId,
                                        String name,
                                        String description,
                                        String status,
                                        boolean editable,
                                        KnowledgeBaseResponse knowledgeBase) {
}
