package com.interview.service.questionbank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestionBankFallbackTerms")
class QuestionBankFallbackTermsTest {

    private final QuestionBankFallbackTerms fallbackTerms = new QuestionBankFallbackTerms();

    @Test
    @DisplayName("extracts ordered unique terms instead of using the whole conversation")
    void shouldExtractOrderedUniqueTerms() {
        assertThat(fallbackTerms.from("请解释 RAG 的基本流程，RAG 会用向量数据库负责相似度检索"))
                .containsExactly("请解释", "RAG", "的基本流程", "会用向量数据库负责相似度检索");
    }

    @Test
    @DisplayName("limits fallback terms to eight")
    void shouldLimitFallbackTerms() {
        assertThat(fallbackTerms.from("aa bb cc dd ee ff gg hh ii jj"))
                .containsExactly("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh");
    }

    @Test
    @DisplayName("falls back to the raw query when no term can be extracted")
    void shouldFallbackToRawQueryWhenNoTermCanBeExtracted() {
        assertThat(fallbackTerms.from("?")).containsExactly("?");
    }
}
