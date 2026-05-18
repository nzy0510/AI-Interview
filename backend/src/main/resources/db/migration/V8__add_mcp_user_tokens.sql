-- V8: Per-user MCP tokens, quota snapshots, and sanitized MCP call logs.

CREATE TABLE IF NOT EXISTS mcp_access_token (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  token_prefix VARCHAR(32) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'READ',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_used_at DATETIME DEFAULT NULL,
  revoked_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mcp_token_hash (token_hash),
  INDEX idx_mcp_token_user_status (user_id, status),
  INDEX idx_mcp_token_status_time (status, update_time),
  CONSTRAINT fk_mcp_token_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Per-user MCP access tokens; only token hashes are stored';

CREATE TABLE IF NOT EXISTS mcp_daily_usage (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  usage_date DATE NOT NULL,
  quota_type VARCHAR(64) NOT NULL,
  used_count INT NOT NULL DEFAULT 0,
  limit_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mcp_daily_usage (user_id, usage_date, quota_type),
  INDEX idx_mcp_usage_date_quota (usage_date, quota_type),
  CONSTRAINT fk_mcp_usage_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Daily MCP quota usage by user and quota type';

CREATE TABLE IF NOT EXISTS mcp_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT DEFAULT NULL,
  token_id BIGINT DEFAULT NULL,
  tool_name VARCHAR(80) NOT NULL,
  success TINYINT(1) NOT NULL DEFAULT 1,
  error_code VARCHAR(80) DEFAULT NULL,
  ip_hash CHAR(64) DEFAULT NULL,
  user_agent_hash CHAR(64) DEFAULT NULL,
  query_hash CHAR(64) DEFAULT NULL,
  query_length INT DEFAULT NULL,
  result_count INT DEFAULT NULL,
  latency_ms INT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_mcp_call_user_time (user_id, create_time),
  INDEX idx_mcp_call_tool_time (tool_name, create_time),
  INDEX idx_mcp_call_success_time (success, create_time),
  CONSTRAINT fk_mcp_call_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_mcp_call_token FOREIGN KEY (token_id) REFERENCES mcp_access_token(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sanitized MCP call log without raw query content';
