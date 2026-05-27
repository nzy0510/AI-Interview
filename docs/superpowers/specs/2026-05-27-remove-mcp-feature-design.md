# Remove MCP Feature Design

## Status

Approved on 2026-05-27.

## Decision

InterWise will completely remove its MCP feature and its dependency on the
standalone `MCP-Skill` repository. The product will not expose `/mcp`, issue
MCP access tokens, deploy an MCP service, or keep a script-accessible
question-bank write endpoint.

Question-bank maintenance is intentionally reduced to one publishing path:

```text
Claude Code + interview-question-bank Skill
-> generate a JSON import package
-> developer signs in to InterWise
-> Settings / Question Bank Admin
-> enter APP_ADMIN_TOKEN
-> validate, dry-run, publish, reindex, and verify
```

Claude Code and the repository Skill remain useful for content generation, but
they do not receive a production publishing capability.

## Context

The main application already owns the core interview and question-bank
runtime:

- Interview RAG retrieves published question-bank atoms directly through the
  Spring Boot `QuestionBankService` and Qdrant.
- The developer-only `QuestionBankAdmin` UI submits imports through
  `/api/admin/question-bank/*`.
- The admin UI is guarded by a developer account check and
  `APP_ADMIN_TOKEN`.

The MCP implementation is now unnecessary for the intended product scope:

- Public external clients will not receive question-bank access.
- Trusted external clients will not use `/mcp-admin` for maintenance.
- Direct script submission through `/internal/question-bank/*` will not be
  supported.

Keeping those paths would expand the deployable surface and create multiple
ways to publish question-bank data without serving a current user need.

## Target Architecture

### Retained Components

- The Spring Boot product runtime and existing authenticated application APIs.
- MySQL question-bank tables and Qdrant retrieval index.
- `QuestionBankService` and interview RAG integration.
- `QuestionBankAdminController` under `/api/admin/question-bank/*`.
- The Vue `QuestionBankAdmin` interface in developer Settings.
- `APP_ADMIN_TOKEN` plus developer-account authorization.
- `scripts/question_bank_import.py` only as a local JSON package generator.
- `skills/interview-question-bank` only as guidance for package generation and
  browser-based admin publication.

### Removed Components

- The `services/mcp-skill` Git submodule and `.gitmodules`.
- Any MCP service build, runtime, reverse proxy route, or deployment variable.
- The user-facing MCP client setup section and its API calls.
- Spring Boot MCP token, quota, and usage runtime code.
- MCP-specific integration and unit tests.
- `/internal/question-bank/*` and `QUESTION_BANK_ADMIN_TOKEN`.
- Script options and code that submit packages directly to a backend.
- Skill guidance that directs maintainers to MCP tools or internal REST
  publishing endpoints.

### Future MCP Work

If MCP is reconsidered later, it must be evaluated as a new independent
service project. It must not be reintroduced as an implicit main-application
deployment dependency without a new approved architecture decision.

## Question Bank Maintenance Flow

The only supported publication workflow is:

1. A maintainer asks Claude Code to generate question-bank material using the
   repository Skill.
2. The generator writes a JSON import package locally for review.
3. A developer account opens the Question Bank Admin panel in Settings.
4. The maintainer enters `APP_ADMIN_TOKEN`, uploads the JSON package, and
   validates it.
5. The maintainer performs a dry run before publishing.
6. Publishing writes accepted atoms to MySQL and synchronizes published data
   to Qdrant using existing `QuestionBankService` behavior.
7. The maintainer verifies results through the admin panel search and reindex
   controls.

The generator must not contain a publish or server-submit mode after this
change.

## Data And Migration Strategy

### Retained Data

The following data belongs to the product and remains in use:

- `knowledge_atom`
- `knowledge_atom_version`
- `knowledge_atom_import_batch`
- other question-bank review or retrieval data
- Qdrant vectors for published question-bank atoms

### Removed Data

These tables exist only to support MCP external access and will be retired from active use:

- `mcp_call_log`
- `mcp_daily_usage`
- `mcp_access_token`
- `mcp_quota_policy`

### Flyway Rule

Previously applied migration history must remain immutable. Do not edit or
delete:

- `V8__add_mcp_user_tokens.sql`
- `V9__add_mcp_quota_policy.sql`

Add a new forward migration after the current latest version. It renames
MCP-only tables to an archival namespace without losing historical rows:

```sql
RENAME TABLE
  mcp_call_log TO retired_mcp_call_log,
  mcp_daily_usage TO retired_mcp_daily_usage,
  mcp_access_token TO retired_mcp_access_token,
  mcp_quota_policy TO retired_mcp_quota_policy;
```

Production operators must back up the database before applying this migration.
Archived MCP token and usage-history data is retained for audit or export, but
is no longer used by runtime features.

## Security Boundary

After this removal:

- The browser admin path is the only write path for question-bank maintenance.
- A request to `/api/admin/question-bank/*` must continue to require an
  authenticated developer account and a valid `APP_ADMIN_TOKEN`.
- No question-bank admin token is accepted by a private REST endpoint.
- No MCP token is generated or stored by the application.
- No external tool protocol endpoint exposes question-bank data.

This removes unused secrets, reduces externally reachable surfaces, and makes
each publication action an explicit operation inside the developer console.

## Deployment Changes

Deployment of the main repository must no longer require another repository:

- Docker Compose builds only the product components: frontend, backend, MySQL,
  Redis, and Qdrant, plus existing HTTPS proxy support where applicable.
- Nginx and Caddy expose no `/mcp` route.
- Deployment instructions use a normal clone and pull workflow without
  submodule initialization.
- Environment templates omit MCP and `QUESTION_BANK_ADMIN_TOKEN` variables.
- Smoke checks verify application health and question-bank admin behavior,
  rather than MCP discovery calls.

## Documentation And Decision History

Update current documentation to describe the active product accurately:

- `README.md`, deployment guides, operations notes, and domain context describe
  the admin-console publication flow without MCP.
- `skills/interview-question-bank/SKILL.md` describes JSON generation and
  browser-admin upload only.
- The question-bank import lifecycle contract names only the Spring Boot
  adapter that remains in this repository.
- Add an ADR that supersedes the standalone MCP and MCP usage-record ADRs,
  preserving history while making the current decision explicit.

Historical documents outside active operation guidance may retain old project
history only when clearly labelled as historical, not current functionality.

## Testing And Verification

The implementation is complete only when all of the following are true:

1. A fresh checkout of the main repository does not need submodule setup.
2. Docker Compose has no `mcp-skill` service or dependency.
3. No proxy configuration exposes `/mcp`.
4. Settings shows the question-bank admin panel for developer users but no MCP
   client setup surface.
5. `/api/mcp/*` and `/internal/question-bank/*` no longer exist.
6. The import generator produces a valid JSON package but cannot submit it.
7. The admin panel can validate, dry-run, publish, reindex, and search a JSON
   import package using the main application.
8. The new Flyway migration removes MCP-only tables on a schema that has
   already applied `V8` and `V9`.
9. Interview RAG and question-bank admin tests continue to pass.
10. Documentation and environment templates contain no instructions that
    present MCP or direct script publication as active functionality.

## Out Of Scope

- Modifying, deleting, or archiving the independent `E:\Develop\MCP-Skill`
  repository.
- Providing any backward-compatible `/mcp` placeholder endpoint.
- Providing a new command-line publishing or direct-write interface.
- Removing question-bank data, Qdrant retrieval, or developer admin-console
  functionality.
