-- V4: User preference for interview defaults
CREATE TABLE IF NOT EXISTS user_preference (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
  default_mode VARCHAR(16) DEFAULT 'text' COMMENT '默认面试模式: text/video',
  default_role VARCHAR(64) DEFAULT NULL COMMENT '默认岗位',
  focus_areas JSON DEFAULT NULL COMMENT '重点关注能力 JSON数组',
  difficulty_level VARCHAR(16) DEFAULT 'mid' COMMENT '默认难度: junior/mid/senior/principal',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户面试偏好';
