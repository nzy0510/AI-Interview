# Complete MCP Feature Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all MCP and direct-script publishing capabilities from InterWise while retaining browser-admin question-bank publication and interview RAG.

**Architecture:** The Spring Boot application remains the sole question-bank runtime and the Vue developer console remains the sole publishing surface. MCP runtime code, submodule deployment, token/quota persistence, and internal script submission are removed; an additive Flyway migration archives already-created MCP tables without rewriting migration history or discarding historical records.

**Tech Stack:** Spring Boot 3 / Java 17 / MyBatis-Plus / Flyway / Vue 3 / Vite / Docker Compose / Nginx / Caddy / Python

---

## File Map

**Retained ownership boundary**

- `backend/src/main/java/com/interview/controller/QuestionBankAdminController.java`: browser admin endpoint; unchanged except tests continue to protect it.
- `backend/src/main/java/com/interview/service/questionbank/QuestionBankService.java`: canonical question-bank import and retrieval logic; retained.
- `frontend/src/components/settings/QuestionBankAdmin.vue`: sole publishing UI; retained.
- `scripts/question_bank_import.py`: local JSON package generator only after this work.

**Files to create**

- `backend/src/main/resources/db/migration/V10__remove_mcp_feature.sql`: forward-only retirement of MCP tables into archival names.
- `docs/adr/0004-remove-mcp-feature.md`: superseding architecture decision.
- `docs/superpowers/plans/2026-05-27-remove-mcp-feature.md`: this implementation plan.

**Files to modify**

- `.gitignore`: track `docs/superpowers/specs/*.md` and `docs/superpowers/plans/*.md`.
- `.env.example`, `.env.prod.example`: remove MCP and direct-script admin variables.
- `backend/src/main/resources/application.yml`, `backend/src/main/resources/application.yml.example`: remove `question-bank.admin-token` and `app.mcp`.
- `frontend/src/views/Settings.vue`, `frontend/src/api/user.js`: remove user-facing MCP entry points.
- `scripts/question_bank_import.py`: remove direct submission support.
- `skills/interview-question-bank/SKILL.md`: package generation plus browser-admin publication only.
- `docker-compose.example.yml`, `docker-compose.prod.yml`, `frontend/nginx.conf`, `Caddyfile`: remove MCP deployment and routing.
- `README.md`, `CONTEXT.md`, `DEPLOYMENT.md`, `AZURE_OPERATIONS.md`, `AGENT.md`, `CHANGELOG.md`, `docs/adr/0002-question-bank-import-lifecycle.md`, `docs/contracts/question-bank-import-lifecycle.md`: describe the active non-MCP product.

**Files to delete**

- `.gitmodules` and the tracked gitlink `services/mcp-skill`.
- `backend/src/main/java/com/interview/controller/McpTokenController.java`
- `backend/src/main/java/com/interview/controller/QuestionBankInternalController.java`
- `backend/src/main/java/com/interview/service/McpTokenService.java`
- `backend/src/main/java/com/interview/dto/McpTokenResponse.java`
- `backend/src/main/java/com/interview/dto/McpUsageResponse.java`
- `backend/src/main/java/com/interview/entity/McpAccessToken.java`
- `backend/src/main/java/com/interview/entity/McpDailyUsage.java`
- `backend/src/main/java/com/interview/entity/McpQuotaPolicy.java`
- `backend/src/main/java/com/interview/mapper/McpAccessTokenMapper.java`
- `backend/src/main/java/com/interview/mapper/McpDailyUsageMapper.java`
- `backend/src/main/java/com/interview/mapper/McpQuotaPolicyMapper.java`
- `backend/src/test/java/com/interview/service/McpTokenServiceTest.java`
- `frontend/src/components/settings/McpClientSetup.vue`
- `skills/interview-question-bank/references/mcp-tools.md`
- `tests/mcp_skill/__init__.py`
- `tests/mcp_skill/test_mcp_quota_policy.py`
- `tests/mcp_skill/test_question_bank_import_contract.py`

**Historical files retained**

- `backend/src/main/resources/db/migration/V8__add_mcp_user_tokens.sql`
- `backend/src/main/resources/db/migration/V9__add_mcp_quota_policy.sql`
- `docs/adr/0001-standalone-question-bank-mcp.md`
- `docs/adr/0003-privacy-preserving-mcp-usage-records.md`
- `docs/updates/2026-05-05-question-bank-mcp-skill.md`

These are historical records. ADR 0004 marks the old ADRs as superseded; old update notes remain historical rather than being rewritten.

### Task 1: Track Design And Plan Artifacts

**Files:**
- Modify: `.gitignore:119-124`
- Track: `docs/superpowers/specs/2026-05-27-remove-mcp-feature-design.md`
- Track: `docs/superpowers/plans/2026-05-27-remove-mcp-feature.md`

- [ ] **Step 1: Add focused ignore exceptions**

Use `apply_patch` to extend the documentation exceptions without exposing generated private document folders:

```diff
 /docs/*
 !/docs/
 !/docs/adr/
 !/docs/adr/*.md
 !/docs/contracts/
 !/docs/contracts/*.md
+!/docs/superpowers/
+!/docs/superpowers/specs/
+!/docs/superpowers/specs/*.md
+!/docs/superpowers/plans/
+!/docs/superpowers/plans/*.md
```

- [ ] **Step 2: Verify only intended superpowers documents become trackable**

Run:

```powershell
git check-ignore -v docs/superpowers/specs/2026-05-27-remove-mcp-feature-design.md docs/superpowers/plans/2026-05-27-remove-mcp-feature.md
git status --short -- .gitignore docs/superpowers
```

Expected: `git check-ignore` prints no ignore rule for either Markdown file; status shows `.gitignore` and this plan if not previously staged, with no private document directory suddenly appearing.

- [ ] **Step 3: Commit documentation tracking policy**

```powershell
git add .gitignore docs/superpowers/specs/2026-05-27-remove-mcp-feature-design.md docs/superpowers/plans/2026-05-27-remove-mcp-feature.md
git commit -m "docs: track superpowers design artifacts"
```

### Task 2: Remove Backend MCP And Internal Write Surfaces

**Files:**
- Delete: `backend/src/main/java/com/interview/controller/McpTokenController.java`
- Delete: `backend/src/main/java/com/interview/controller/QuestionBankInternalController.java`
- Delete: `backend/src/main/java/com/interview/service/McpTokenService.java`
- Delete: `backend/src/main/java/com/interview/dto/McpTokenResponse.java`
- Delete: `backend/src/main/java/com/interview/dto/McpUsageResponse.java`
- Delete: `backend/src/main/java/com/interview/entity/McpAccessToken.java`
- Delete: `backend/src/main/java/com/interview/entity/McpDailyUsage.java`
- Delete: `backend/src/main/java/com/interview/entity/McpQuotaPolicy.java`
- Delete: `backend/src/main/java/com/interview/mapper/McpAccessTokenMapper.java`
- Delete: `backend/src/main/java/com/interview/mapper/McpDailyUsageMapper.java`
- Delete: `backend/src/main/java/com/interview/mapper/McpQuotaPolicyMapper.java`
- Delete: `backend/src/test/java/com/interview/service/McpTokenServiceTest.java`
- Modify: `backend/src/main/resources/application.yml:146-191`
- Modify: `backend/src/main/resources/application.yml.example:146-180`
- Test: `backend/src/test/java/com/interview/controller/QuestionBankAdminControllerTest.java`
- Test: `backend/src/test/java/com/interview/service/questionbank/QuestionBankImportContractTest.java`

- [ ] **Step 1: Record baseline tests for retained admin and import behavior**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=QuestionBankAdminControllerTest,QuestionBankImportContractTest test
```

Expected: PASS, proving the browser-admin and question-bank service paths already work before removal.

- [ ] **Step 2: Delete the two unsupported endpoint families and their MCP-only types**

Use `apply_patch` delete hunks for the listed controllers, MCP service, DTOs, entities, mappers, and `McpTokenServiceTest`. Do not modify `QuestionBankAdminController` or question-bank DTOs.

After deletion, these searches must return no active Java sources:

```powershell
rg -n "McpTokenController|McpTokenService|McpAccessToken|McpDailyUsage|McpQuotaPolicy|McpTokenResponse|McpUsageResponse|QuestionBankInternalController" backend/src/main/java backend/src/test/java
```

Expected: no matches.

- [ ] **Step 3: Remove unused configuration bindings**

In both application configuration files, keep Qdrant and bootstrap properties, but reduce the relevant structure to:

```yaml
# 题库 / Qdrant 配置
question-bank:
  qdrant:
    enabled: ${QDRANT_ENABLED:true}
    url: ${QDRANT_URL:http://localhost:6333}
    collection: ${QDRANT_COLLECTION:interview_atoms}
    vector-size: ${QDRANT_VECTOR_SIZE:384}
  bootstrap:
    seed-from-json: ${QUESTION_BANK_SEED_FROM_JSON:true}
    reindex-unsynced-on-startup: ${QUESTION_BANK_REINDEX_ON_STARTUP:true}
  legacy-loader:
    enabled: ${QUESTION_BANK_LEGACY_LOADER_ENABLED:false}
```

Under `app:`, retain `admin-token`, analytics, rate limit, quota, and developer whitelist, and delete:

```yaml
  mcp:
    public-url: ${APP_MCP_PUBLIC_URL:}
```

- [ ] **Step 4: Verify retained backend behavior and removed symbols**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=QuestionBankAdminControllerTest,QuestionBankImportContractTest,InterviewServiceImplTest test
rg -n "McpToken|McpAccess|McpDaily|McpQuota|QuestionBankInternal|APP_MCP_PUBLIC_URL|QUESTION_BANK_ADMIN_TOKEN" backend/src/main backend/src/test
```

Expected: Maven tests PASS; search returns matches only in historical migration SQL until Task 3 adds its forward cleanup migration.

- [ ] **Step 5: Commit backend surface removal**

```powershell
git add backend/src/main backend/src/test
git commit -m "refactor: remove MCP backend surfaces"
```

### Task 3: Drop MCP-Only Database Tables With A Forward Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__remove_mcp_feature.sql`
- Retain unchanged: `backend/src/main/resources/db/migration/V8__add_mcp_user_tokens.sql`
- Retain unchanged: `backend/src/main/resources/db/migration/V9__add_mcp_quota_policy.sql`

- [ ] **Step 1: Create the new migration**

Add exactly:

```sql
-- V10: Retire MCP feature persistence without discarding historical records.

RENAME TABLE
  mcp_call_log TO retired_mcp_call_log,
  mcp_daily_usage TO retired_mcp_daily_usage,
  mcp_access_token TO retired_mcp_access_token,
  mcp_quota_policy TO retired_mcp_quota_policy;
```

Renaming the related tables in one statement retains the existing rows while taking the MCP schema out of active use.

- [ ] **Step 2: Verify migration history was not rewritten**

Run:

```powershell
git diff --exit-code -- backend/src/main/resources/db/migration/V8__add_mcp_user_tokens.sql backend/src/main/resources/db/migration/V9__add_mcp_quota_policy.sql
Get-Content backend/src/main/resources/db/migration/V10__remove_mcp_feature.sql
```

Expected: the diff command exits `0`; `V10` renames four MCP tables to `retired_mcp_*` so historical records are retained without active runtime use.

- [ ] **Step 3: Run the backend test suite**

Run:

```powershell
mvn -f backend/pom.xml test
```

Expected: PASS.

- [ ] **Step 4: Commit the Flyway cleanup**

```powershell
git add backend/src/main/resources/db/migration/V10__remove_mcp_feature.sql
git commit -m "refactor: remove MCP persistence tables"
```

### Task 4: Remove The User-Facing MCP Setup UI

**Files:**
- Modify: `frontend/src/views/Settings.vue:111,144`
- Modify: `frontend/src/api/user.js:75-89`
- Delete: `frontend/src/components/settings/McpClientSetup.vue`

- [ ] **Step 1: Add a frontend regression assertion for the retained auth behavior**

The project does not currently test `Settings.vue` rendering. Keep existing test scope narrow by ensuring current request authentication remains green before editing:

```powershell
npm --prefix frontend exec vitest -- --run src/utils/__tests__/auth.test.js
```

Expected: PASS.

- [ ] **Step 2: Remove the MCP panel import and rendering**

In `Settings.vue`, remove only:

```vue
<McpClientSetup />
```

and:

```javascript
import McpClientSetup from '@/components/settings/McpClientSetup.vue'
```

Keep:

```vue
<QuestionBankAdmin />
```

inside the developer-only Operations section.

- [ ] **Step 3: Remove obsolete API exports and component**

Delete `McpClientSetup.vue`. From `frontend/src/api/user.js`, delete exactly these exports:

```javascript
export const getMcpTokenAPI = () => {
    return request({ url: '/mcp/token', method: 'get' });
}

export const generateMcpTokenAPI = () => {
    return request({ url: '/mcp/token', method: 'post' });
}

export const revokeMcpTokenAPI = () => {
    return request({ url: '/mcp/token', method: 'delete' });
}

export const getMcpUsageAPI = () => {
    return request({ url: '/mcp/usage', method: 'get' });
}
```

- [ ] **Step 4: Verify frontend build and absence of MCP UI references**

Run:

```powershell
npm --prefix frontend exec vitest -- --run
npm --prefix frontend run build
rg -n "McpClientSetup|getMcpTokenAPI|generateMcpTokenAPI|revokeMcpTokenAPI|getMcpUsageAPI|/mcp/token|/mcp/usage" frontend/src
```

Expected: Vitest and build PASS; search returns no matches.

- [ ] **Step 5: Commit UI removal**

```powershell
git add frontend/src
git commit -m "refactor: remove MCP client setup UI"
```

### Task 5: Make The Skill A Package-Generation-Only Workflow

**Files:**
- Modify: `scripts/question_bank_import.py:1-12,276-285,335-385`
- Modify: `skills/interview-question-bank/SKILL.md`
- Delete: `skills/interview-question-bank/references/mcp-tools.md`
- Retain: `skills/interview-question-bank/references/atom-schema.md`
- Retain: `skills/interview-question-bank/references/review-rubric.md`

- [ ] **Step 1: Remove HTTP submission from the generator**

Delete `submit_package(...)` and eliminate the submission arguments:

```python
parser.add_argument("--submit", action="store_true", help="Submit package to the backend import API.")
parser.add_argument(
    "--api-url",
    default=os.getenv("QUESTION_BANK_IMPORT_URL", "http://localhost:8080/internal/question-bank/import"),
)
parser.add_argument("--token", default=os.getenv("QUESTION_BANK_ADMIN_TOKEN"))
```

Reduce the program tail to local output and validation status only:

```python
    if package["validationReport"]["errors"]:
        print("validation errors:")
        for error in package["validationReport"]["errors"]:
            print(f"  - {error}")
    return 0 if not package["validationReport"]["errors"] else 2
```

Update the module help examples to contain no `--submit` command.

- [ ] **Step 2: Rewrite the Skill workflow around browser publication**

Replace the publication/reindex/verify instructions with:

```markdown
4. Give the reviewed JSON package to the maintainer for upload through the developer-only Question Bank Admin panel in Settings.
5. In the panel, enter `APP_ADMIN_TOKEN`, run validation and dry-run, then publish only after the maintainer confirms the preview.
6. Verify the published atoms and index status through the panel search and reindex controls.
```

Retain generation examples without submission:

```powershell
python scripts/question_bank_import.py --input .\materials\java --category java --mode DRAFT
python scripts/question_bank_import.py --input .\materials\redis.pdf --category redis --mode AUTO_PUBLISH
```

Delete the `mcp-tools.md` reference and remove any instruction to read it.

- [ ] **Step 3: Verify no direct publishing path remains in generator or Skill**

Run:

```powershell
python -m py_compile scripts/question_bank_import.py
rg -n -- "--submit|submit_package|QUESTION_BANK_ADMIN_TOKEN|QUESTION_BANK_IMPORT_URL|internal/question-bank|mcp-admin|submit_atom_import_package|reindex_question_bank|search_interview_atoms" scripts/question_bank_import.py skills/interview-question-bank
```

Expected: Python compile succeeds; search returns no matches.

- [ ] **Step 4: Commit generator and Skill consolidation**

```powershell
git add scripts/question_bank_import.py skills/interview-question-bank
git commit -m "refactor: limit question bank skill to package generation"
```

### Task 6: Remove MCP Deployment, Proxying, And Submodule Coupling

**Files:**
- Delete: `.gitmodules`
- Delete: tracked gitlink `services/mcp-skill`
- Modify: `docker-compose.example.yml`
- Modify: `docker-compose.prod.yml`
- Modify: `.env.example`
- Modify: `.env.prod.example`
- Modify: `frontend/nginx.conf`
- Modify: `Caddyfile`

- [ ] **Step 1: Remove MCP service construction and dependencies**

From both tracked Compose templates, delete the complete:

```yaml
  mcp-skill:
    ...
```

service block. Remove `- mcp-skill` from `frontend.depends_on` and
`caddy.depends_on`. From backend environments remove:

```yaml
QUESTION_BANK_ADMIN_TOKEN: ${QUESTION_BANK_ADMIN_TOKEN}
APP_MCP_PUBLIC_URL: ${APP_MCP_PUBLIC_URL:-...}
```

Do not edit the ignored developer-local `docker-compose.yml`; it can be regenerated from the corrected example template if needed.

- [ ] **Step 2: Remove environment variables for retired surfaces**

In `.env.example` and `.env.prod.example`, retain Qdrant/bootstrap variables and remove:

```env
QUESTION_BANK_ADMIN_TOKEN=...
MCP_ALLOWED_ORIGINS=...
APP_MCP_PUBLIC_URL=...
MCP_PUBLIC_MAX_LIMIT=...
MCP_MAX_QUERY_LENGTH=...
```

Rename the section heading to:

```env
# --- 题库 / Qdrant ---
```

- [ ] **Step 3: Remove public MCP routes**

From `frontend/nginx.conf`, delete:

```nginx
    # Public standalone MCP endpoint. Do not proxy /mcp-admin here.
    location = /mcp {
        proxy_pass http://mcp-skill:8765;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }
```

From both HTTP and HTTPS site blocks in `Caddyfile`, delete the `@mcp` matcher and its `handle @mcp` reverse proxy block, leaving the frontend reverse proxy as the only route.

- [ ] **Step 4: Remove the Git submodule**

First verify that the independent repository remains available and that the
embedded checkout has no uncommitted work:

```powershell
git -C E:\Develop\MCP-Skill rev-parse --show-toplevel
git -C E:\Develop\interview\services\mcp-skill status --short
```

Expected: the first command reports `E:/Develop/MCP-Skill`; the second command
prints no local changes.

Delete `.gitmodules` with `apply_patch`, then remove the tracked gitlink and
embedded submodule checkout from the main repository:

```powershell
git rm -f -- services/mcp-skill
```

Do not modify or delete the independent checkout at `E:\Develop\MCP-Skill`.

- [ ] **Step 5: Validate deployment templates and routing absence**

Run:

```powershell
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --services
rg -n "mcp-skill|/mcp|APP_MCP|MCP_ALLOWED|MCP_PUBLIC|QUESTION_BANK_ADMIN_TOKEN|services/mcp-skill|submodule" .gitmodules .env.example .env.prod.example docker-compose.example.yml docker-compose.prod.yml frontend/nginx.conf Caddyfile 2>$null
```

Expected: Compose lists `db`, `redis`, `qdrant`, `backend`, `frontend`, and optionally `caddy`, but not `mcp-skill`; search returns no matches in the deployment surface.

- [ ] **Step 6: Commit deployment decoupling**

```powershell
git add .gitmodules .env.example .env.prod.example docker-compose.example.yml docker-compose.prod.yml frontend/nginx.conf Caddyfile services/mcp-skill
git commit -m "refactor: detach MCP deployment dependency"
```

### Task 7: Supersede MCP Documentation And Record The Product Change

**Files:**
- Create: `docs/adr/0004-remove-mcp-feature.md`
- Modify: `docs/adr/0001-standalone-question-bank-mcp.md`
- Modify: `docs/adr/0003-privacy-preserving-mcp-usage-records.md`
- Modify: `docs/adr/0002-question-bank-import-lifecycle.md`
- Modify: `docs/contracts/question-bank-import-lifecycle.md`
- Modify: `README.md`
- Modify: `CONTEXT.md`
- Modify: `DEPLOYMENT.md`
- Modify: `AZURE_OPERATIONS.md`
- Modify: `AGENT.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add the superseding ADR**

Create `docs/adr/0004-remove-mcp-feature.md`:

```markdown
# Remove MCP Feature From InterWise

Status: accepted

InterWise no longer exposes question-bank MCP endpoints or deploys the standalone MCP service. Question-bank publication is performed only through the developer-only web administration panel, where a developer account supplies `APP_ADMIN_TOKEN`, validates a generated JSON import package, runs a dry run, and explicitly publishes it.

This decision supersedes ADR 0001 and ADR 0003. The question bank, Qdrant retrieval, interview RAG, and local package-generation Skill remain product capabilities. The `MCP-Skill` repository is no longer a source or deployment dependency of this repository.
```

Add to ADR 0001 and ADR 0003 immediately after their status:

```markdown
Superseded by: [ADR 0004: Remove MCP Feature From InterWise](0004-remove-mcp-feature.md)
```

- [ ] **Step 2: Narrow the import lifecycle contract**

In ADR 0002 and `docs/contracts/question-bank-import-lifecycle.md`, remove the Python MCP adapter claim and state:

```markdown
The executable contract is enforced by the Java `QuestionBankService`, which powers the developer-only web administration workflow and interview retrieval. Claude Code and the repository Skill generate JSON import packages for manual upload; they do not publish directly.
```

- [ ] **Step 3: Rewrite current-facing project guidance**

Update `README.md`, `CONTEXT.md`, `DEPLOYMENT.md`, `AZURE_OPERATIONS.md`, and `AGENT.md` so current operation says:

```markdown
- 题库维护：Claude Code / Skill 生成 JSON 导入包，开发者在 Settings 的题库后台使用 `APP_ADMIN_TOKEN` 校验、试运行和发布。
- 部署组件：frontend + backend + mysql + redis + qdrant。
- 外部 MCP 接入已从当前产品范围移除。
```

Remove live instructions for `/mcp`, `/mcp-admin`, `/internal/question-bank/*`, MCP tokens, submodule cloning, and MCP environment variables. Preserve historical update notes rather than rewriting `docs/updates/2026-05-05-question-bank-mcp-skill.md`.

Apply these file-specific edits:

- `README.md`: remove MCP from the opening feature list, Mermaid architecture,
  directory tree, default endpoints, security guidance, and maintenance
  examples; show `Script -> JSON Package -> QuestionBankAdmin UI ->
  QuestionBankService -> MySQL/Qdrant -> Interview RAG`.
- `CONTEXT.md`: remove definitions and invariants for Standalone MCP Service,
  public/admin MCP endpoints, MCP token, quota, usage record, and desensitized
  MCP result; define the Question Bank Maintenance Skill as a package
  generation workflow and the Developer Admin Console as the only publication
  surface.
- `DEPLOYMENT.md`: remove submodule clone/update commands, MCP service logs,
  endpoint checks, and MCP environment configuration; require checking the
  developer question-bank admin flow after deployment.
- `AZURE_OPERATIONS.md`: remove MCP origin/token procedures and replace related
  acceptance checks with browser-admin publication/reindex verification.
- `AGENT.md`: list only `frontend + backend + mysql + redis + qdrant` in
  deployment and state that question-bank publication occurs through the
  developer admin UI after package generation.

- [ ] **Step 4: Record the removal in the changelog**

Under `## 未发布` in `CHANGELOG.md`, add:

```markdown
### 移除
- 完整下线外部 MCP 接入、MCP token/额度能力与 `MCP-Skill` 子模块部署依赖。
- 移除脚本直提题库入口，题库发布统一收敛至开发者后台的校验、试运行与正式发布流程。
```

- [ ] **Step 5: Verify active documentation no longer advertises MCP**

Run:

```powershell
rg -n "MCP|mcp|submodule|QUESTION_BANK_ADMIN_TOKEN|/internal/question-bank" README.md CONTEXT.md DEPLOYMENT.md AZURE_OPERATIONS.md AGENT.md docs/adr docs/contracts skills/interview-question-bank .env.example .env.prod.example
```

Expected: matches occur only in ADR 0001/0003 historical content and ADR 0004's removal explanation, not as active instructions or supported functionality.

- [ ] **Step 6: Commit documentation updates**

```powershell
git add README.md CONTEXT.md DEPLOYMENT.md AZURE_OPERATIONS.md AGENT.md CHANGELOG.md docs/adr docs/contracts
git commit -m "docs: record MCP feature retirement"
```

### Task 8: Final Verification

**Files:**
- Review all changed files and deletions from Tasks 1-7.

- [ ] **Step 1: Confirm repository no longer depends on MCP**

Run:

```powershell
git status --short --branch
git ls-files .gitmodules services/mcp-skill tests/mcp_skill
rg -n "McpToken|McpAccess|McpDaily|McpQuota|QuestionBankInternal|/api/mcp|/internal/question-bank|services/mcp-skill|mcp-skill|APP_MCP|MCP_ALLOWED|MCP_PUBLIC|QUESTION_BANK_ADMIN_TOKEN" backend/src frontend/src scripts skills README.md CONTEXT.md DEPLOYMENT.md AZURE_OPERATIONS.md AGENT.md docker-compose.example.yml docker-compose.prod.yml frontend/nginx.conf Caddyfile .env.example .env.prod.example
```

Expected: no tracked submodule/test paths; search returns no active-code or active-guidance matches.

- [ ] **Step 2: Run application verification**

Run:

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend exec vitest -- --run
npm --prefix frontend run build
python -m py_compile scripts/question_bank_import.py
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --services
```

Expected: all commands PASS; Compose output contains no `mcp-skill`.

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git diff master...HEAD --stat
git diff master...HEAD --name-status
```

Expected: every file traces to MCP removal, admin-only question-bank publication, migration cleanup, or corresponding documentation; untracked `.agents/` and `AGENTS.md` are absent from commits.

- [ ] **Step 4: Commit any verification-only corrections**

If verification requires a narrowly scoped correction, apply it, repeat the failing command, then commit:

```powershell
git add <corrected-files>
git commit -m "fix: complete MCP feature removal verification"
```
