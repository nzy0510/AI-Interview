package com.interview.service.questionbank.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankBuildCategoryPlannerTest {
    private final QuestionBankBuildCategoryPlanner planner = new QuestionBankBuildCategoryPlanner();

    @Test
    void shouldParseAStableCategoryCatalog() {
        assertThat(planner.parse("""
                {"categories":["服务注册与发现","配置中心","服务调用与负载均衡"]}
                """))
                .containsExactly("服务注册与发现", "配置中心", "服务调用与负载均衡");
    }

    @Test
    void shouldRejectFallbackCategoriesThatWouldCollapseCoverage() {
        assertThatThrownBy(() -> planner.parse("{\"categories\":[\"通用\",\"配置中心\"]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能使用通用");
    }

    @Test
    void shouldLimitDocumentExcerptsSentToThePlanner() {
        String prompt = planner.userPrompt(List.of(
                new QuestionBankBuildCategoryPlanner.SourceExcerpt("notes.md", 0, "a".repeat(20_000))));

        assertThat(prompt.length()).isLessThan(3_000);
        assertThat(prompt).contains("notes.md").doesNotContain("a".repeat(2_501));
    }
}
