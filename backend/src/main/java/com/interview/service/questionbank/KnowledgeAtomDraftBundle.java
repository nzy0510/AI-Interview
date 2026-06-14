package com.interview.service.questionbank;

import java.util.List;

public record KnowledgeAtomDraftBundle(
        List<KnowledgeAtomDraft> atoms,
        boolean atomLimitReached
) {
}
