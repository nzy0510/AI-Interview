package com.interview.service.questionbank;

import java.util.List;

public record KnowledgeBaseResponse(Long id,
                                    String scope,
                                    Long ownerUserId,
                                    Long positionId,
                                    String name,
                                    String status,
                                    List<KnowledgeSourceFileResponse> sourceFiles) {
}
