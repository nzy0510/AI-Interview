package com.interview.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewPrompts")
class InterviewPromptsTest {

    private InterviewPrompts prompts;

    @BeforeEach
    void setUp() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "interview.prompts.attitude-rule", "态度监控规则测试内容",
                "interview.prompts.coordinator", "你是面试组长。测试。",
                "interview.prompts.technical", "你是一位资深技术面试官。测试。",
                "interview.prompts.hr", "你是资深 HR BP。测试。",
                "interview.prompts.closing", "你是面试组长。测试结束语。",
                "interview.prompts.evaluation", "你现在是一个面试评估分析师。测试。",
                "interview.prompts.resume-analysis", "你是一个超级资深的猎头。测试。"
        ));
        prompts = new Binder(source).bind("interview.prompts", InterviewPrompts.class).get();
    }

    @Test
    @DisplayName("应加载全部 7 段提示词")
    void shouldLoadAllSevenPrompts() {
        assertThat(prompts.getAttitudeRule()).contains("态度监控");
        assertThat(prompts.getCoordinator()).contains("面试组长");
        assertThat(prompts.getTechnical()).contains("技术面试官");
        assertThat(prompts.getHr()).contains("HR BP");
        assertThat(prompts.getClosing()).contains("结束语");
        assertThat(prompts.getEvaluation()).contains("评估分析师");
        assertThat(prompts.getResumeAnalysis()).contains("猎头");
    }

    @Test
    @DisplayName("态度监控规则不应为空")
    void attitudeRuleShouldNotBeBlank() {
        assertThat(prompts.getAttitudeRule()).isNotBlank();
    }
}
