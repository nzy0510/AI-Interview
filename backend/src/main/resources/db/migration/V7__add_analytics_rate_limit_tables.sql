-- V7: Product analytics, feedback and daily quota tracking.

CREATE TABLE IF NOT EXISTS app_event_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT DEFAULT NULL COMMENT 'Authenticated user id when available',
  anonymous_id VARCHAR(64) DEFAULT NULL COMMENT 'Frontend anonymous visitor id',
  event_type VARCHAR(64) NOT NULL COMMENT 'Normalized event type, e.g. PAGE_VIEW',
  event_category VARCHAR(64) DEFAULT NULL COMMENT 'product/api/security/system',
  path VARCHAR(255) DEFAULT NULL COMMENT 'Request path or frontend page',
  http_method VARCHAR(16) DEFAULT NULL,
  status_code INT DEFAULT NULL,
  success TINYINT(1) NOT NULL DEFAULT 1,
  error_code VARCHAR(64) DEFAULT NULL,
  error_message VARCHAR(300) DEFAULT NULL,
  ip_hash CHAR(64) DEFAULT NULL COMMENT 'SHA-256 hash, raw IP is not stored',
  user_agent_hash CHAR(64) DEFAULT NULL COMMENT 'SHA-256 hash, raw UA is not stored',
  request_id VARCHAR(64) DEFAULT NULL,
  latency_ms BIGINT DEFAULT NULL,
  metadata_json JSON DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_event_time (event_type, create_time),
  INDEX idx_user_time (user_id, create_time),
  INDEX idx_anon_time (anonymous_id, create_time),
  INDEX idx_path_time (path, create_time),
  INDEX idx_success_time (success, create_time),
  INDEX idx_status_time (status_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sanitized product and API event log';

CREATE TABLE IF NOT EXISTS user_daily_usage (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  usage_date DATE NOT NULL,
  quota_type VARCHAR(64) NOT NULL COMMENT 'interview_start/ai_chat_turn/resume_parse/mentor_generate',
  used_count INT NOT NULL DEFAULT 0,
  limit_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date_quota (user_id, usage_date, quota_type),
  INDEX idx_date_quota (usage_date, quota_type),
  INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Daily quota usage snapshot';

CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT DEFAULT NULL,
  category VARCHAR(64) NOT NULL DEFAULT 'general',
  content TEXT NOT NULL,
  contact VARCHAR(255) DEFAULT NULL,
  page_url VARCHAR(500) DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  ip_hash CHAR(64) DEFAULT NULL,
  user_agent_hash CHAR(64) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status_time (status, create_time),
  INDEX idx_user_time (user_id, create_time),
  INDEX idx_category_time (category, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User feedback inbox';
