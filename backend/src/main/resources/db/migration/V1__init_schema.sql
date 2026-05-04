-- V1: Base schema for AI Interview platform
-- Requires MySQL 8.0+

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '哈希加密后的密码',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '绑定邮箱',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像链接',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS `interview_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '关联的用户ID',
  `position` VARCHAR(64) NOT NULL COMMENT '面试岗位',
  `chat_history` JSON NOT NULL COMMENT '完整面试对话历史 (JSON格式存储)',
  `score` INT DEFAULT NULL COMMENT 'AI综合评分',
  `feedback` TEXT DEFAULT NULL COMMENT 'AI评价反馈',
  `ability_json` JSON DEFAULT NULL COMMENT '六维能力评分',
  `recommendations` JSON DEFAULT NULL COMMENT 'AI提供的职场或技术提升建议列表',
  `voice_wpm` INT DEFAULT 0 COMMENT '面试平均语速 (Words Per Minute)',
  `emotion_json` TEXT DEFAULT NULL COMMENT '视频面试情感分析数据',
  `interview_mode` VARCHAR(16) DEFAULT 'text' COMMENT '面试模式: text=文字模式, video=视频模式',
  `knowledge_json` TEXT DEFAULT NULL COMMENT '星系知识图谱数据',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '面试开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '面试结束时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试记录表';

CREATE TABLE IF NOT EXISTS `resume_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '所属用户',
  `position` VARCHAR(100) DEFAULT '软件开发' COMMENT '目标岗位',
  `analysis_json` LONGTEXT COMMENT 'AI解析后的完整JSON画像',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户简历画像';

