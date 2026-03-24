-- AI 面试系统数据库初始化脚本
-- 如果数据库不存在则创建
CREATE DATABASE IF NOT EXISTS `ai_interview_ds` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ai_interview_ds`;

-- 1. 用户账号表
-- 存储用户的登录信息及基础资料
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '哈希加密后的密码',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像链接',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号表';

-- 2. 面试记录表
-- 存储面试全过程，包括历史对话内容、评分及专业建议
DROP TABLE IF EXISTS `interview_record`;
CREATE TABLE `interview_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '关联的用户ID',
  `position` VARCHAR(64) NOT NULL COMMENT '面试岗位',
  `chat_history` JSON NOT NULL COMMENT '完整面试对话历史 (JSON格式存储)',
  `score` INT DEFAULT NULL COMMENT 'AI综合评分',
  `feedback` TEXT DEFAULT NULL COMMENT 'AI评价反馈',
  `ability_json` JSON DEFAULT NULL COMMENT '六维能力评分 (包含：技术深度、解题思路、知识广度、表达、逻辑、应变)',
  `recommendations` JSON DEFAULT NULL COMMENT 'AI提供的职场或技术提升建议列表',
  `voice_wpm` INT DEFAULT 0 COMMENT '面试平均语速 (Words Per Minute)',
  `emotion_json` TEXT DEFAULT NULL COMMENT '视频面试情感分析数据 (JSON格式，含情绪分布、自信指数等)',
  `interview_mode` VARCHAR(16) DEFAULT 'text' COMMENT '面试模式: text=文字模式, video=视频模式',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '面试开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '面试结束时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录表';

-- 注入默认管理员数据
-- 默认用户名：admin ， 密码：123456
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `nickname`) VALUES (1, 'admin', '123456', '系统管理员');
