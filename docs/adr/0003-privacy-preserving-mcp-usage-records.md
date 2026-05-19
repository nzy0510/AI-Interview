# Privacy-Preserving MCP Usage Records

Status: accepted

InterWise records public MCP usage for quota enforcement, abuse detection, and operational debugging, but it does not store raw user query text. Usage records may keep identifiers, tool names, query hashes or lengths, result counts, latency, status, and hashed request metadata, preserving enough signal to operate the service without turning MCP logs into prompt transcripts.

The effective daily quota policy is stored in `mcp_quota_policy` and read by both the Java Settings API and the standalone MCP service. `mcp_daily_usage.limit_count` is a per-day snapshot used for enforcement history, not the policy source.
