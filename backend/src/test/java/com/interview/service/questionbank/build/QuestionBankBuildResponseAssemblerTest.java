package com.interview.service.questionbank.build;

import com.interview.entity.QuestionBankBuildCandidate;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuestionBankBuildResponseAssemblerTest {
    @Test
    void shouldCarryServerOwnedSourceFileIntoInternalAtomPayload() {
        QuestionBankBuildResponseAssembler assembler = new QuestionBankBuildResponseAssembler(
                mock(KnowledgeSourceFileMapper.class), mock(AppJobMapper.class));
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setStableAtomId("stable-1");
        candidate.setSourceFileId(301L);
        candidate.setSubject("JVM");
        candidate.setCategory("java");
        candidate.setDifficulty("mid");
        candidate.setTagsJson("[]");
        candidate.setPrinciples("双亲委派");
        candidate.setFollowUpPathsJson("[\"深入\",\"引导\"]");
        candidate.setSourceRef("notes.md#chunk-0");
        candidate.setSourceEvidenceJson("[{\"quote\":\"双亲委派\"}]");

        assertThat(assembler.toPayload(candidate).getSourceFileId()).isEqualTo(301L);
    }
}
