package com.interview.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        config.setPositionCategories(Map.of(
                "java", List.of("hot200", "mysql", "redis", "spring", "springboot", "并发", "操作系统", "common"),
                "前端", List.of("hot200", "common")
        ));
    }

    @Test
    @DisplayName("Java 岗位匹配 8 个分类")
    void shouldReturnEightCategoriesForJavaPosition() {
        List<String> categories = config.getCategoriesFor("Java 后端开发");
        assertThat(categories)
                .hasSize(8)
                .contains("hot200", "mysql", "redis", "spring", "springboot", "并发", "操作系统", "common");
    }

    @Test
    @DisplayName("前端岗位匹配 2 个分类")
    void shouldReturnTwoCategoriesForFrontendPosition() {
        List<String> categories = config.getCategoriesFor("前端工程师");
        assertThat(categories)
                .hasSize(2)
                .contains("hot200", "common");
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
        assertThat(categories).hasSize(8);
    }
}
