package com.interview.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class DatabaseUpdater {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void updateDb() {
        try {
            jdbcTemplate.execute("ALTER TABLE interview_record ADD COLUMN knowledge_json TEXT DEFAULT NULL COMMENT '星系知识图谱数据' AFTER interview_mode");
            log.info("✅ 成功添加 knowledge_json 字段");
        } catch (Exception e) {
            log.info("ℹ️ knowledge_json 字段可能已存在: {}", e.getMessage());
        }
    }
}
