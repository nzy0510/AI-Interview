-- Create database if not exists
CREATE DATABASE IF NOT EXISTS `ai_interview_ds` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ai_interview_ds`;

-- Users Table
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Username',
  `password` VARCHAR(255) NOT NULL COMMENT 'Password (hashed)',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT 'Display Name',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT 'Avatar URL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update Time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User Account Table';

-- Interview Records Table
CREATE TABLE IF NOT EXISTS `interview_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT 'Associated User ID',
  `position` VARCHAR(64) NOT NULL COMMENT 'Interview Position (e.g. Java Backend, Frontend)',
  `chat_history` JSON NOT NULL COMMENT 'Full Chat History (JSON)',
  `score` INT DEFAULT NULL COMMENT 'AI Evaluated Score (0-100)',
  `feedback` TEXT DEFAULT NULL COMMENT 'AI Evaluated Feedback',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Start Time',
  `end_time` DATETIME DEFAULT NULL COMMENT 'End Time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Interview Records Table';

-- Insert dummy test user (password: 123456 encrypted assuming plain text mapping for testing, or we handle encryption in java)
-- Note: Replace password logic with BCrypt later in Java backend
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `nickname`) VALUES (1, 'admin', '123456', 'Admin User');
