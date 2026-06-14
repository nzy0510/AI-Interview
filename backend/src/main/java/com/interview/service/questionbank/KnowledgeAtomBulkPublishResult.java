package com.interview.service.questionbank;

public record KnowledgeAtomBulkPublishResult(
        Long sourceFileId,
        int matched,
        int published,
        int synced,
        int failed,
        int skipped
) {
}
