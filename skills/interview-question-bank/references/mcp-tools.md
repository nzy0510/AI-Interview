# MCP Tools

The project exposes a stateless JSON-RPC MCP endpoint at `POST /mcp`.

Read tools:

- `search_interview_atoms`: semantic search against published atoms. Inputs include `position`, `query`, `categories`, `excludeAtomIds`, and `limit`.
- `get_interview_atom`: fetch one atom by `atomId`.
- `list_interview_categories`: show category counts by status.
- `generate_interview_context`: search and format hits as prompt context for an interviewer agent.

Maintenance tools:

- `validate_atom_import_package`: validate an import package without writing atoms.
- `submit_atom_import_package`: import atoms as `DRAFT` or `AUTO_PUBLISH`.
- `reindex_question_bank`: rebuild Qdrant vectors for all published atoms.

Authentication:

- Read calls honor `question-bank.mcp.read-token` when configured.
- Maintenance calls honor `question-bank.admin-token` when configured.
- Tokens may be sent as `X-MCP-Token`, `X-Question-Bank-Token`, or `Authorization: Bearer <token>`.

Client meaning:

- When the current project agent calls the service directly, it is using the local backend as part of the application.
- When an external AI client calls `/mcp`, the same question-bank operations are available outside the app without giving that client database credentials or source-code access.
