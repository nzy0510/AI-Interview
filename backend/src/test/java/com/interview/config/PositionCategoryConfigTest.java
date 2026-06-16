package com.interview.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PositionCategoryConfig")
class PositionCategoryConfigTest {

    private PositionCategoryConfig config;

    @BeforeEach
    void setUp() {
        config = new PositionCategoryConfig();
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("java", List.of("hot200", "mysql", "redis", "spring", "springboot", "并发", "操作系统"));
        categories.put("前端", List.of("hot200", "Vue"));
        categories.put("AI大模型", List.of("大模型基础", "模型微调", "RAG", "向量数据库", "Agent", "开发框架",
                "MCP协议", "A2A协议", "Prompt工程", "推理部署", "应用实践"));
        config.setPositionCategories(categories);
    }

    @Test
    @DisplayName("Java 岗位匹配技术分类且不混入 HR 软技能来源")
    void shouldReturnTechnicalCategoriesForJavaPosition() {
        List<String> categories = config.getCategoriesFor("Java 后端开发");
        assertThat(categories)
                .hasSize(7)
                .contains("hot200", "mysql", "redis", "spring", "springboot", "并发", "操作系统")
                .doesNotContain("common", "HR软技能");
    }

    @Test
    @DisplayName("前端岗位匹配技术分类且不混入 HR 软技能来源")
    void shouldReturnFrontendTechnicalCategories() {
        List<String> categories = config.getCategoriesFor("Web 前端开发");
        assertThat(categories)
                .hasSize(2)
                .contains("hot200", "Vue")
                .doesNotContain("common", "HR软技能");
    }

    @Test
    @DisplayName("未匹配岗位抛出 IllegalArgumentException")
    void shouldThrowForUnknownPosition() {
        assertThatThrownBy(() -> config.getCategoriesFor("DevOps"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("Javascript 不应匹配 Java 规则")
    void shouldNotMatchJavascriptAsJava() {
        assertThatThrownBy(() -> config.getCategoriesFor("Javascript"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("大小写不敏感匹配")
    void shouldMatchCaseInsensitive() {
        List<String> categories = config.getCategoriesFor("JAVA 开发工程师");
        assertThat(categories).hasSize(7);
    }

    @Test
    @DisplayName("AI 大模型岗位匹配大模型题库分类")
    void shouldReturnAiModelCategoryForAiModelPosition() {
        List<String> categories = config.getCategoriesFor("AI 大模型工程师");

        assertThat(categories)
                .containsExactly("大模型基础", "模型微调", "RAG", "向量数据库", "Agent", "开发框架",
                        "MCP协议", "A2A协议", "Prompt工程", "推理部署", "应用实践");
    }
}
