package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 岗位到知识库分类的映射配置，从 application.yml 的 interview.position-categories 读取。
 * 匹配时使用词边界检查，避免 "Javascript" 误匹配 "java"。
 */
@ConfigurationProperties(prefix = "interview")
public class PositionCategoryConfig {

    private Map<String, List<String>> positionCategories = Collections.emptyMap();

    public Map<String, List<String>> getPositionCategories() {
        return positionCategories;
    }

    public void setPositionCategories(Map<String, List<String>> positionCategories) {
        this.positionCategories = positionCategories;
    }

    /**
     * 根据岗位名匹配分类列表。使用正则词边界确保 "Javascript" 不会误匹配 "java"。
     * 未匹配任何配置时抛出 IllegalArgumentException。
     */
    public List<String> getCategoriesFor(String position) {
        for (Map.Entry<String, List<String>> entry : positionCategories.entrySet()) {
            String key = entry.getKey();
            Pattern pattern = Pattern.compile(
                    "(?<![a-zA-Z])" + Pattern.quote(key) + "(?![a-zA-Z])",
                    Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(position).find()) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("未配置岗位对应的知识库分类: " + position);
    }
}
