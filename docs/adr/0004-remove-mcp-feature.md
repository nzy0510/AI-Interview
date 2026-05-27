# Remove MCP Feature From InterWise

Status: accepted

InterWise no longer exposes question-bank MCP endpoints or deploys the standalone MCP service. Question-bank publication is performed only through the developer-only web administration panel, where a developer account supplies `APP_ADMIN_TOKEN`, validates a generated JSON import package, runs a dry run, and explicitly publishes it.

This decision supersedes ADR 0001 and ADR 0003. The question bank, Qdrant retrieval, interview RAG, and local package-generation Skill remain product capabilities. The `MCP-Skill` repository is no longer a source or deployment dependency of this repository.

Existing MCP token, quota, and call-log tables are renamed with a `retired_mcp_` prefix during migration so historical records remain available for audit or export while no active product path reads or writes them.
