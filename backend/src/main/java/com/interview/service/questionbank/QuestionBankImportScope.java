package com.interview.service.questionbank;

public record QuestionBankImportScope(
        String scope,
        Long ownerUserId,
        Long positionId,
        Long knowledgeBaseId,
        Long currentUserId,
        boolean allowAutoPublish,
        boolean syncOnPublish
) {
    public QuestionBankImportScope(String scope,
                                   Long ownerUserId,
                                   Long positionId,
                                   Long knowledgeBaseId,
                                   Long currentUserId,
                                   boolean allowAutoPublish) {
        this(scope, ownerUserId, positionId, knowledgeBaseId, currentUserId, allowAutoPublish, true);
    }
}
