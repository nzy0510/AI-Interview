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
        // interview_record 表动态补列
        addColumnQuietly("interview_record", "ability_json", "VARCHAR(255) DEFAULT NULL COMMENT '六维能力评级'");
        addColumnQuietly("interview_record", "recommendations", "TEXT DEFAULT NULL COMMENT '提升建议列表'");
        addColumnQuietly("interview_record", "voice_wpm", "INT DEFAULT NULL COMMENT '平均语速(WPM)'");
        addColumnQuietly("interview_record", "emotion_json", "TEXT DEFAULT NULL COMMENT '视频面试情感分析数据'");
        addColumnQuietly("interview_record", "interview_mode", "VARCHAR(50) DEFAULT 'text' COMMENT '面试模式'");
        addColumnQuietly("interview_record", "knowledge_json", "TEXT DEFAULT NULL COMMENT '星系知识图谱数据'");

        // 简历画像持久化表（幂等建表）
        createResumeProfileTable();

        // user 表补充 email 字段
        addColumnQuietly("user", "email", "VARCHAR(128) DEFAULT NULL COMMENT '绑定邮箱' AFTER `password`");
    }

    private void createResumeProfileTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS resume_profile (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL COMMENT '所属用户',
                    position VARCHAR(100) DEFAULT '软件开发' COMMENT '目标岗位',
                    analysis_json LONGTEXT COMMENT 'AI解析后的完整JSON画像',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户简历画像'
            """);
            log.info("✅ resume_profile 表就绪");
        } catch (Exception e) {
            log.error("❌ resume_profile 表创建失败: {}", e.getMessage());
        }
    }

    private void addColumnQuietly(String table, String columnName, String columnDef) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + columnName + " " + columnDef);
            log.info("✅ 成功添加 {}.{} 字段", table, columnName);
        } catch (Exception e) {
            log.trace("ℹ️ {}.{} 字段可能已存在或跳过创建", table, columnName);
        }
    }
}

