-- V10: Retire MCP feature persistence without discarding historical records.

RENAME TABLE
  mcp_call_log TO retired_mcp_call_log,
  mcp_daily_usage TO retired_mcp_daily_usage,
  mcp_access_token TO retired_mcp_access_token,
  mcp_quota_policy TO retired_mcp_quota_policy;
