package com.interview.service.questionbank.build;

import com.interview.entity.QuestionBankBuildCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankBuildCandidateValidatorTest {
    @Test
    void shouldRejectCandidateCategoryOutsideThePlannedCategoryCatalog() {
        QuestionBankBuildCandidate candidate = validCandidate();
        candidate.setCategory("通用");

        assertThatThrownBy(() -> new QuestionBankBuildCandidateValidator()
                .validate(candidate, List.of("服务注册与发现", "配置中心")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不在本批次分类范围");
    }

    private QuestionBankBuildCandidate validCandidate() {
        QuestionBankBuildCandidate candidate = new QuestionBankBuildCandidate();
        candidate.setSubject("Nacos 服务注册");
        candidate.setCategory("服务注册与发现");
        candidate.setDifficulty("mid");
        candidate.setPrinciples("客户端向注册中心登记服务实例");
        candidate.setFollowUpPathsJson("[\"深入追问\",\"引导追问\"]");
        candidate.setSourceRef("notes.md#chunk-0");
        candidate.setSourceEvidenceJson("[{\"quote\":\"客户端向注册中心登记服务实例\"}]");
        return candidate;
    }
}
