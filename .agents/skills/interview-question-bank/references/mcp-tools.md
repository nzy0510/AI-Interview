# MCP Tools

InterWise exposes question-bank MCP through the standalone MCP-Skill service, not the Spring Boot main app. Public clients call `POST /mcp`; maintainers call `POST /mcp-admin` through a trusted route such as localhost, private network, or SSH tunnel.

Public read tools at `/mcp`:

- `search_interview_atoms`: semantic search against published atoms. Inputs include `position`, `query`, `categories`, `excludeAtomIds`, and `limit`.
- `get_interview_atom_summary`: fetch a desensitized summary for one atom by `atomId`.
- `list_interview_categories`: show category counts by status.
- `generate_interview_context`: search and format hits as prompt context for an interviewer agent.
- `get_mcp_usage_status`: show the caller's MCP quota usage.

Admin tools at `/mcp-admin`:

- `search_interview_atoms`
- `get_interview_atom`: fetch the full atom for maintenance.
- `list_interview_categories`
- `generate_interview_context`
- `validate_atom_import_package`: validate an import package without writing atoms.
- `submit_atom_import_package`: import atoms as `DRAFT` or `AUTO_PUBLISH`.
- `reindex_question_bank`: rebuild Qdrant vectors for all published atoms.

Authentication:

- Public calls use a per-user token generated in InterWise Settings and stored hashed in the main app database.
- Admin calls use `QUESTION_BANK_ADMIN_TOKEN`.
- Tokens may be sent as `X-MCP-Token`, `X-Question-Bank-Token`, or `Authorization: Bearer <token>`.

Client meaning:

- The InterWise product runtime uses `QuestionBankService` directly for interviews and RAG.
- External AI clients use the standalone MCP service so they can access approved question-bank capability without database credentials or Spring Boot source-code access.
