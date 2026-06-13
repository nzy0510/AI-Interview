# User-Owned Question Bank, RAG, And Report Redesign Issues

Status: Ready for issue-tracker publishing
Date: 2026-06-13
Parent spec: `docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

This file expands the approved tracer-bullet breakdown into issue-ready bodies.
No issue tracker or triage label vocabulary has been configured yet, so issue
references are temporary local identifiers.

## Publishing Notes

- Publish in dependency order.
- Replace `IQB-*` identifiers with real tracker issue IDs after creation.
- Keep `HITL` issues as explicit review gates.
- Each `AFK` issue should be implementable by an agent without new product
  decisions, assuming its blockers are complete.

## IQB-01: 建立岗位/知识库/原子作用域模型与破坏性迁移

Type: HITL

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Introduce the new public/private ownership model for positions, knowledge bases,
knowledge atoms, user roles, interview turns, reports, and jobs. Migrate the
existing built-in question bank into three explicit public positions, preserve
user-facing account data, and clear old interview/report data according to the
approved destructive migration policy.

### Acceptance criteria

- [ ] The system has explicit public positions for `Java 后端开发`, `Web 前端开发`, and `AI 大模型应用开发`.
- [ ] Public positions each have a default public knowledge base.
- [ ] Private positions and knowledge bases can be represented with owner scope.
- [ ] Existing public question-bank atoms are migrated into the new scoped model.
- [ ] Old interview records, old report-derived data, and old interview-tied RAG logs are cleared.
- [ ] User accounts, user LLM Provider configs, resume profiles, and feedback are preserved.
- [ ] The bootstrap admin account can be configured for username `nzy333` and email `1525764737@qq.com`.
- [ ] Migration behavior is documented and repeatable in local Docker testing.

### Blocked by

None - can start immediately.

## IQB-02: 管理员角色与公共/私有资源权限闭环

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Make administrator capability part of the normal authenticated user model.
Administrators can manage public positions, public knowledge bases, and admin
authorization. Ordinary users can view public content, copy it into their own
workspace, and manage only their own private content.

### Acceptance criteria

- [ ] Admin authorization uses JWT plus user role, not `APP_ADMIN_TOKEN` in the product flow.
- [ ] Admin users can grant and revoke admin role for other users while preserving at least one admin.
- [ ] Ordinary users cannot create, edit, publish, reindex, or archive public resources.
- [ ] Ordinary users cannot read or modify another user's private positions, files, atoms, jobs, or reports.
- [ ] Admin-only navigation is hidden from ordinary users and protected by backend authorization.
- [ ] Permission tests cover public, private, cross-user, and admin-only paths.

### Blocked by

- IQB-01

## IQB-03: 统一异步任务表与本地 TaskExecutor 执行器

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Create a recoverable local job system for import, atom generation, atom review,
publication, reindexing, and report generation. Jobs are persisted in the
database, claimed by backend workers, exposed through polling APIs, and retryable
when failures are user-actionable.

### Acceptance criteria

- [ ] Jobs support `PENDING`, `RUNNING`, `FAILED`, and `COMPLETED` states.
- [ ] Jobs track type, scope, owner, position, knowledge base, source file or interview record, stage, progress, sanitized error message, retryability, retry count, lock owner, and lock expiry.
- [ ] Backend startup re-enqueues pending jobs and expired running jobs.
- [ ] Failed jobs are not retried automatically unless the user requests retry.
- [ ] Job polling returns only jobs visible to the current user or admin.
- [ ] Job lifecycle tests cover claim, complete, fail, retry, and restart recovery.

### Blocked by

- IQB-01

## IQB-04: 文件存储与 MarkItDown 文档转换服务

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Add the first document import tracer path: upload a supported knowledge file,
store the original file through the storage abstraction, convert it to Markdown
through a Python MarkItDown service, store the converted Markdown, and expose the
conversion result through the job/status model.

### Acceptance criteria

- [ ] Supported upload types are PDF, DOCX, Markdown/MD, and TXT.
- [ ] Files larger than 20 MB are rejected with a clear user-facing error.
- [ ] Original files and converted Markdown are stored through a `FileStorageService` abstraction.
- [ ] The first implementation uses a local mounted storage path suitable for Docker Compose.
- [ ] The backend calls a `document-converter` service instead of launching Python directly inside Java request handling.
- [ ] Upload and download/read endpoints validate ownership or public read rules.
- [ ] Path traversal and unsupported file type tests are covered.

### Blocked by

- IQB-03

## IQB-05: 私有岗位知识库管理页面 MVP

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Add a first-level knowledge-bank page where ordinary users can create private
positions, view their default knowledge base, upload files, and track conversion
or import progress. The page should make public positions read-only and private
positions user-managed.

### Acceptance criteria

- [ ] The sidebar exposes a `知识库 / 题库` entry for logged-in users.
- [ ] Users can create, view, and archive their own private positions.
- [ ] A new private position automatically has one default knowledge base.
- [ ] Users can upload supported files into their own position knowledge base.
- [ ] The page shows file status, conversion status, and associated job status.
- [ ] Public positions are visible but not editable by ordinary users.
- [ ] UI state tests or component tests cover normal user visibility and disabled public edit actions where feasible.

### Blocked by

- IQB-01
- IQB-02
- IQB-03
- IQB-04

## IQB-06: LLM 原子生成与二审审查流程

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Turn converted Markdown into interview-ready atom drafts using the current user's
active LLM Provider, then run LLM review over those drafts. The flow should keep
generation and review results visible to the user while preventing publication
of rejected content.

### Acceptance criteria

- [ ] Atom generation uses the active LLM Provider of the user who owns the private import or the admin performing a public import.
- [ ] If no active Provider exists, atom generation and review are blocked with a clear configuration prompt.
- [ ] A single import creates at most 100 atom drafts and marks `atomLimitReached` when the cap is hit.
- [ ] LLM review stores `PASS`, `NEEDS_REVIEW`, or `REJECT` with reason, confidence, and suggested patch where applicable.
- [ ] `REJECT` atoms cannot be published in the first version.
- [ ] LLM errors are sanitized and do not expose API keys, tokens, or sensitive headers.
- [ ] Tests cover provider-required gating, atom cap handling, review status parsing, and sanitized failure messages.

### Blocked by

- IQB-05

## IQB-07: 原子人工确认、版本发布与 Qdrant 索引

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Let users review generated atoms, accept suggested patches, make lightweight
manual edits, create manual atoms, publish eligible atoms, and synchronize their
vectors into Qdrant with strict ownership payloads.

### Acceptance criteria

- [ ] `PASS` atoms can be published directly by the owner or admin.
- [ ] `NEEDS_REVIEW` atoms require accepting a patch or manual handling before publication.
- [ ] Editing a published atom creates a draft revision instead of overwriting the published version in place.
- [ ] Publishing replaces the current searchable version and retains old versions as read-only history.
- [ ] Published atoms are embedded and upserted into Qdrant with scope, owner, position, knowledge base, atom id, and status payload.
- [ ] Vector status reflects pending, synced, failed, or reindex-required states.
- [ ] Tests cover publication eligibility, version replacement, failed indexing, and payload filter fields.

### Blocked by

- IQB-06

## IQB-08: 公共题库管理员导入与维护流程

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Reuse the same import, review, publication, and reindex pipeline for
administrator-managed public positions. Admins can maintain public starter
content while ordinary users continue to see public content as read-only.

### Acceptance criteria

- [ ] Admin users can upload files into public position knowledge bases.
- [ ] Public imports use the active LLM Provider of the admin performing the action.
- [ ] Admins can review, publish, archive, and reindex public atoms.
- [ ] Ordinary users can view public positions and public usability status but cannot mutate public content.
- [ ] Public position and public atom operations are covered by admin and normal-user permission tests.
- [ ] Existing developer-only import tooling is not required for normal public maintenance.

### Blocked by

- IQB-02
- IQB-06
- IQB-07

## IQB-09: 公共岗位复制为我的岗位

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Allow ordinary users to copy a public position into their own workspace. The
copied position becomes private, receives a default private knowledge base, and
gets private copies of the published public atoms with a separate private Qdrant
index.

### Acceptance criteria

- [ ] Users can copy any active public position into their own private positions.
- [ ] Copied atoms become private, owned by the copying user, and remain published.
- [ ] Copying does not rerun LLM review.
- [ ] Copying triggers private embedding and Qdrant upsert with private scope payload.
- [ ] Public updates do not silently mutate an existing user's private copy.
- [ ] Tests cover copy isolation, duplicate copy naming or conflict behavior, and private index payloads.

### Blocked by

- IQB-07
- IQB-08

## IQB-10: 面试准备页切换到结构化岗位选择与可用性校验

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Replace free-form or legacy interview position selection with structured public
and private position selection. The setup flow must clearly show whether a
position is interview-ready and block interviews when quality prerequisites are
not met.

### Acceptance criteria

- [ ] Interview setup lists public positions and the current user's private positions separately.
- [ ] Position status includes usable, no knowledge, indexing, index failed, and archived.
- [ ] Archived positions cannot start new interviews.
- [ ] Private positions with no published and synced atoms cannot start interviews.
- [ ] Qdrant/index unavailable states block interview start instead of silently degrading retrieval quality.
- [ ] Frontend and backend guards both enforce the same start-interview rules.

### Blocked by

- IQB-01
- IQB-07
- IQB-09

## IQB-11: 面试 RAG 检索切换为严格作用域过滤

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Cut over interview retrieval to the new scoped Qdrant payload model. Public
interviews must retrieve only selected public-position atoms, and private
interviews must retrieve only atoms owned by the current user for the selected
private position.

### Acceptance criteria

- [ ] Public retrieval always filters by public scope and selected public position.
- [ ] Private retrieval always filters by private scope, current user, and selected private position.
- [ ] Interview retrieval never performs an unscoped vector query.
- [ ] Interview retrieval does not silently fall back to MySQL when Qdrant is unavailable or empty.
- [ ] Retrieval filter construction is controlled by backend services, not optional frontend input.
- [ ] Tests cover public retrieval, private retrieval, cross-user isolation, and no-fallback behavior.

### Blocked by

- IQB-10

## IQB-12: 结构化记录每轮面试对话与检索快照

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Persist each interview turn as structured data rather than relying only on raw
chat history. Each turn should retain the question, user answer, phase,
retrieved atom references, prompt context snapshot, retrieval strategy, and
timestamps needed for stable report generation.

### Acceptance criteria

- [ ] New interview records bind to the selected position.
- [ ] Each technical or HR turn stores AI question, user answer, phase, and turn index.
- [ ] Technical turns store retrieved atom ids and enough snapshot data for historical reports.
- [ ] Report generation can use stored turn data even if atoms are later edited or archived.
- [ ] Existing interview UX continues to work while writing structured turn data.
- [ ] Tests cover turn persistence, phase classification, and atom snapshot retention.

### Blocked by

- IQB-11

## IQB-13: 异步报告生成与逐题评分报告

Type: AFK

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Move end-of-interview report generation into the unified job system. Reports
should show generating, completed, or failed states, and completed reports should
include per-question scoring, reference answers or directions, improvement
suggestions, and visible answer source labels.

### Acceptance criteria

- [ ] Ending an interview creates a `GENERATE_REPORT` job instead of blocking on full report generation.
- [ ] The UI can show report generating, completed, failed, and retry states.
- [ ] Failed report generation keeps the interview data and can be retried.
- [ ] Technical and HR questions receive a 0-10 combined per-question score.
- [ ] Opening and closing turns are not scored as individual report items.
- [ ] Report items include question, user answer, score, reference answer or direction, improvement suggestion, and source label.
- [ ] Source labels distinguish knowledge-base references, AI-generated technical references, and HR guidance.
- [ ] Tests cover report job lifecycle, score parsing, source labels, and historical snapshot stability.

### Blocked by

- IQB-03
- IQB-12

## IQB-14: 旧流程收口、文档更新与回归验证

Type: HITL

### Parent

`docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

### What to build

Close the migration by removing or downgrading old developer-only paths that no
longer belong in the normal product workflow, updating user and developer docs,
and running the end-to-end verification matrix for local self-deployment and
future cloud platform readiness.

### Acceptance criteria

- [ ] Old admin-token question-bank workflow is removed from the product path or clearly marked developer-only.
- [ ] Documentation explains public positions, private positions, copying public positions, user LLM Provider requirements, import limits, and report generation states.
- [ ] README, CHANGELOG, and relevant architecture docs are updated.
- [ ] Backend tests cover permissions, migrations, jobs, Qdrant filters, file upload, and report parsing.
- [ ] Frontend tests or manual verification cover knowledge-bank navigation, interview setup guards, and report states.
- [ ] Security review covers path traversal, cross-user access, admin role checks, secret redaction, and unscoped retrieval.
- [ ] Local Docker verification demonstrates upload, atom review, publish, interview, and async report generation.

### Blocked by

- IQB-01
- IQB-02
- IQB-03
- IQB-04
- IQB-05
- IQB-06
- IQB-07
- IQB-08
- IQB-09
- IQB-10
- IQB-11
- IQB-12
- IQB-13
