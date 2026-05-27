# Standalone Question Bank MCP Boundary

Status: accepted

Superseded by: [ADR 0004: Remove MCP Feature From InterWise](0004-remove-mcp-feature.md)

InterWise exposes the question bank to external AI clients through a standalone MCP service instead of the Spring Boot main app, so external tooling can evolve and deploy without coupling to the user-facing interview runtime. The public `/mcp` endpoint is intentionally read-only, per-user-token based, and limited to published, desensitized results; full maintenance workflows stay behind local-only `/mcp-admin` with the question-bank admin token and are not exposed as the ordinary user integration path.
