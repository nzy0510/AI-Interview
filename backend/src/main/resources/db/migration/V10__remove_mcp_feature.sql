-- V10: Remove MCP feature persistence after the external MCP surface was retired.

DROP TABLE IF EXISTS mcp_call_log;
DROP TABLE IF EXISTS mcp_daily_usage;
DROP TABLE IF EXISTS mcp_access_token;
DROP TABLE IF EXISTS mcp_quota_policy;
