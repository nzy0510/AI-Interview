package com.interview.service.questionbank;

public record KnowledgePositionResponse(Long id,
                                        String scope,
                                        Long ownerUserId,
                                        String name,
                                        String description,
                                        String status,
                                        boolean editable,
                                        boolean canImportPackage,
                                        boolean canManageAtoms,
                                        boolean canPublishAtoms,
                                        boolean canReindexAtoms,
                                        boolean canArchiveAtoms,
                                        KnowledgeBaseResponse knowledgeBase) {
}
