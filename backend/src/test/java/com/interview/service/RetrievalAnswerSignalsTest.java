package com.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetrievalAnswerSignals")
class RetrievalAnswerSignalsTest {

    private final RetrievalAnswerSignals signals = new RetrievalAnswerSignals();

    @Test
    @DisplayName("short technical answers expand retrieval candidates")
    void shouldExpandRetrievalLimitForShortTechnicalAnswer() {
        assertThat(signals.effectiveRetrievalLimit("低秩矩阵", 20, 30)).isEqualTo(30);
    }

    @Test
    @DisplayName("mixed technical answers expand retrieval candidates")
    void shouldExpandRetrievalLimitForMixedTechnicalAnswer() {
        assertThat(signals.effectiveRetrievalLimit(
                "项目里同时用了 RAG、Embedding、向量库和 Agent 工具调用，但细节有点混在一起",
                20,
                30))
                .isEqualTo(30);
    }

    @Test
    @DisplayName("low-information answers keep the default candidate budget")
    void shouldKeepDefaultRetrievalLimitForLowInformationAnswer() {
        assertThat(signals.effectiveRetrievalLimit("我不会", 20, 30)).isEqualTo(20);
    }

    @Test
    @DisplayName("normal single-topic answers keep the default candidate budget")
    void shouldKeepDefaultRetrievalLimitForNormalSingleTopicAnswer() {
        assertThat(signals.effectiveRetrievalLimit(
                "先检索相关文档，再把上下文交给模型生成，最后根据上下文回答问题",
                20,
                30))
                .isEqualTo(20);
    }

    @Test
    @DisplayName("short technical terms are not treated as low information")
    void shouldNotTreatShortTechnicalTermsAsLowInformation() {
        assertThat(signals.isLowInformationAnswer("LoRA")).isFalse();
        assertThat(signals.isLowInformationAnswer("嗯")).isTrue();
    }
}
