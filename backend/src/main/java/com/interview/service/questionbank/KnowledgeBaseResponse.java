package com.interview.service.questionbank;

public record KnowledgeBaseResponse(Long id,
                                    String scope,
                                    Long ownerUserId,
                                    Long positionId,
                                    String name,
                                    String status) {
}
