package com.interview.service.questionbank.build;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.entity.QuestionBankBuild;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankBuildCandidateFactoryTest {
    @Test
    void shouldNotFabricateSourceEvidenceWhenGeneratorOmitsIt() {
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(9L); build.setOwnerUserId(7L); build.setPositionId(8L); build.setKnowledgeBaseId(10L);
        build.setCategoriesJson("[\"java\"]");
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(11L); sourceFile.setFileHash("hash"); sourceFile.setOriginalFilename("notes.md");
        JSONObject atom = JSON.parseObject("""
                {"subject":"JVM","category":"java","difficulty":"mid","content":{"principles":"类加载","followUpPaths":["深入","引导"]}}
                """);

        var candidate = QuestionBankBuildCandidateFactory.create(
                build, sourceFile, 0, 0, atom, 0);

        assertThat(candidate.getSourceEvidenceJson()).isEqualTo("[]");
    }

    @Test
    void shouldRejectGeneratorOmittingCategoryInsteadOfSilentlyUsingTheFirstCatalogEntry() {
        QuestionBankBuild build = new QuestionBankBuild();
        build.setId(9L); build.setOwnerUserId(7L); build.setPositionId(8L); build.setKnowledgeBaseId(10L);
        build.setCategoriesJson("[\"类加载机制\",\"内存管理\"]");
        KnowledgeSourceFile sourceFile = new KnowledgeSourceFile();
        sourceFile.setId(11L); sourceFile.setFileHash("hash"); sourceFile.setOriginalFilename("notes.md");
        JSONObject atom = JSON.parseObject("""
                {"subject":"JVM","difficulty":"mid","content":{"principles":"类加载","followUpPaths":["深入","引导"]},
                 "sourceEvidence":[{"quote":"类加载"}]}
                """);

        assertThatThrownBy(() -> QuestionBankBuildCandidateFactory.create(
                build, sourceFile, 0, 0, atom, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主题、分类、难度和原则不能为空");
    }
}
