-- V9: Shared MCP quota policy for Java Settings display and standalone MCP enforcement.

CREATE TABLE IF NOT EXISTS mcp_quota_policy (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(32) NOT NULL,
  quota_type VARCHAR(64) NOT NULL,
  label VARCHAR(100) NOT NULL,
  limit_count INT NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mcp_quota_policy (role_name, quota_type),
  INDEX idx_mcp_quota_role_order (role_name, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shared MCP daily quota policy by token role and quota type';

INSERT INTO mcp_quota_policy (role_name, quota_type, label, limit_count, display_order)
VALUES
  ('READ', 'total', '每日 MCP 总调用', 150, 10),
  ('READ', 'search', '每日题库检索', 80, 20),
  ('READ', 'context', '每日上下文生成', 40, 30),
  ('READ', 'detail', '每日题目摘要读取', 60, 40),
  ('READ', 'categories', '每日分类查看', 30, 50),
  ('READ', 'usage_status', '每日额度查询', 30, 60),
  ('DEVELOPER', 'total', '每日 MCP 总调用', 2000, 10),
  ('DEVELOPER', 'search', '每日题库检索', 1000, 20),
  ('DEVELOPER', 'context', '每日上下文生成', 500, 30),
  ('DEVELOPER', 'detail', '每日题目摘要读取', 500, 40),
  ('DEVELOPER', 'categories', '每日分类查看', 300, 50),
  ('DEVELOPER', 'usage_status', '每日额度查询', 300, 60)
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  display_order = VALUES(display_order);
