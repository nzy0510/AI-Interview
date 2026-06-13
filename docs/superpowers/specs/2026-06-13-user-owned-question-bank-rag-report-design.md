# User-Owned Question Bank, RAG, And Report Redesign

Status: Draft for user review
Date: 2026-06-13
Scope: InterWise question-bank ownership, document import, RAG retrieval, interview reports, and public/private platform boundaries.

## 1. Background

InterWise is moving from a developer-maintained question-bank workflow to a model where ordinary users can run their own interview practice platform:

- Self-hosted users can deploy locally and manage their own interview knowledge base.
- Cloud deployment can serve multiple ordinary users, each with isolated positions, knowledge files, LLM configuration, interviews, and reports.
- The existing built-in question bank remains as public starter content, but public content must be structured, scoped, and protected.

The current implementation is centered on a developer-only Question Bank Admin panel protected by `APP_ADMIN_TOKEN`. It uses JSON import packages generated outside the application, persists global `knowledge_atom` data, and maps interview positions to categories through static configuration. This redesign replaces that with first-class user-owned positions and knowledge bases while keeping the existing RAG interview concept.

## 2. Goals

- Make question-bank management usable by ordinary logged-in users.
- Preserve the built-in public question bank as three explicit public positions.
- Let users create private positions and manage private knowledge files.
- Support PDF, DOCX, Markdown, and TXT import through an in-app workflow.
- Convert uploaded files to Markdown, generate knowledge atoms, run LLM review, then require user confirmation before publication.
- Use the user's active LLM Provider for all LLM work.
- Keep public and private RAG retrieval strictly isolated.
- Block interviews when the selected position has no published and indexed knowledge.
- Generate interview reports asynchronously with per-question scoring and references.
- Support both local Docker self-deployment and future cloud platform deployment.

## 3. Non-Goals For The First Version

- No external message queue such as RabbitMQ or Kafka.
- No automatic publication immediately after upload.
- No PPT, Excel, image OCR, URL crawling, or zip batch import.
- No interview-time tag filtering.
- No multi-knowledge-base composition per position.
- No multiple LLM Provider routing per task.
- No team/shared workspace model.
- No full commercial quota or billing system.
- No physical deletion of positions in the normal product flow.

## 4. Confirmed Product Decisions

### 4.1 Public And Private Positions

The built-in public question bank is migrated into three explicit public positions:

1. `Java 后端开发`
2. `Web 前端开发`
3. `AI 大模型应用开发`

Each public position has its own public knowledge base. Ordinary users can use public positions for interview practice but cannot edit public content.

Users can create private positions. A private position belongs to exactly one user and only retrieves that user's private knowledge atoms.

### 4.2 Public Position Copy

Ordinary users can copy a public position into their own workspace:

- Create a private position.
- Create a private default knowledge base.
- Copy public atoms into private atom rows.
- Keep copied atoms published.
- Rebuild private Qdrant vectors with private scope payload.
- Public updates do not automatically affect the user's private copy.

The copy flow does not rerun LLM review because public content has already been curated by an administrator.

### 4.3 Admin Role

The old `APP_ADMIN_TOKEN` model is removed from the product flow.

Administrators are normal user accounts with an admin role:

- Initial admin defaults to:
  - username: `nzy333`
  - email: `1525764737@qq.com`
- This value should be configuration/bootstrap data, not hardcoded business logic.
- Admin users can authorize other admins.
- Admin users can manage public positions and public knowledge bases.
- Ordinary users can only manage their own private content.

### 4.4 One Active LLM Provider

The first version keeps the current single active Provider model:

- Users may save multiple Provider configs.
- Exactly one Provider is active at a time.
- All LLM operations use the active Provider.
- There is no system fallback key.
- If no active Provider exists, LLM-dependent features are blocked with a clear prompt.

This includes interview turns, atom generation, atom review, report generation, and AI Mentor analysis.

### 4.5 File Import Limits

The first version uses conservative limits:

- Single file max size: 20 MB.
- Single import max atoms: 100.
- Supported file types: PDF, DOCX, Markdown/MD, TXT.
- If the atom cap is reached, the system marks `atomLimitReached=true` and tells the user that the document may not be fully covered.

The system should parse the whole document, ask the LLM to extract the most interview-relevant 100 atoms, and avoid silently dropping content without warning.

## 5. Target Information Architecture

### 5.1 User Navigation

Add a first-level sidebar entry:

```text
知识库 / 题库
```

Ordinary users see:

- Public positions, read-only, with copy action.
- My positions.
- Files under the selected position.
- Draft/reviewed/published atoms under the selected position.
- Import jobs and retry actions.

Admin users additionally see:

- Public question-bank management.
- Public position management.
- User role management.

### 5.2 Knowledge-Base Page Sections

The first version uses four areas:

1. Position list
   - Public positions.
   - My positions.
   - Status: usable, no knowledge, converting, generating, reviewing, indexing, failed, archived.

2. Knowledge files
   - Upload PDF/DOCX/MD/TXT.
   - Store original file and converted Markdown.
   - Show conversion status.
   - Support re-convert, regenerate atoms, archive/delete file assets.

3. Knowledge atoms
   - Draft, reviewed, published, reindex required, archived.
   - Review statuses: PASS, NEEDS_REVIEW, REJECT.
   - Filter by source file, tag, review status, publication status.
   - Manual atom create/edit.

4. Jobs
   - Import progress.
   - Failed stage.
   - Sanitized error message.
   - Retry action.

### 5.3 Position To Knowledge Base

The first version uses one default knowledge base per position.

That knowledge base can contain many files, topics, tags, imports, and atoms:

```text
Position
  -> Default Knowledge Base
      -> Source Files
      -> Import Batches
      -> Knowledge Atoms
```

The first version does not expose multiple named knowledge bases under one position. Tags are used for organization inside the default knowledge base.

## 6. Data Model Direction

The exact table names may be adjusted during implementation, but the model should preserve these concepts.

### 6.1 User Role

Add role support to the existing user model.

Suggested fields:

- `role`: `USER` / `ADMIN`
- `admin_granted_by`
- `admin_granted_at`

Bootstrap logic:

- If the registered user matches configured bootstrap admin username/email, grant admin role.
- Admin users can grant or revoke admin role for other users.
- At least one admin should remain.

### 6.2 Position

New table: `interview_position`

Core fields:

- `id`
- `scope`: `PUBLIC` / `PRIVATE`
- `owner_user_id`: null for public, user id for private
- `name`
- `description`
- `status`: `ACTIVE` / `ARCHIVED`
- `default_knowledge_base_id`
- `created_by`
- `create_time`
- `update_time`

Rules:

- Public positions are admin-managed.
- Private positions are user-managed.
- Archived positions cannot start new interviews.

### 6.3 Knowledge Base

New table: `knowledge_base`

Core fields:

- `id`
- `scope`
- `owner_user_id`
- `position_id`
- `name`
- `status`: `ACTIVE` / `ARCHIVED`
- `created_by`
- `create_time`
- `update_time`

First version uses one default knowledge base per position.

### 6.4 Source File

New table: `knowledge_source_file`

Core fields:

- `id`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `original_filename`
- `content_type`
- `file_size`
- `file_hash`
- `storage_key`
- `markdown_storage_key`
- `domain_tags_json`
- `status`: `UPLOADED` / `CONVERTING` / `CONVERTED` / `FAILED` / `ARCHIVED`
- `error_message`
- `created_by`
- `create_time`
- `update_time`

The original file and Markdown are stored outside the database through `FileStorageService`.

### 6.5 Knowledge Atom

The current `knowledge_atom` model should be migrated, not kept as a separate legacy structure.

Required new concepts:

- `scope`: `PUBLIC` / `PRIVATE`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `source_file_id`
- `current_version_no`
- `review_status`: `PASS` / `NEEDS_REVIEW` / `REJECT` / `UNREVIEWED`
- `review_reason`
- `review_confidence`
- `suggested_patch_json`
- `publication_status`: `DRAFT` / `PUBLISHED` / `ARCHIVED`
- `vector_status`: `PENDING` / `SYNCED` / `FAILED` / `REINDEX_REQUIRED`
- `tags_json`
- `source_ref`
- `checksum`

Existing atom fields such as subject, difficulty, principles, pitfalls, and follow-up paths remain.

### 6.6 Atom Versioning

Published atoms are not overwritten directly.

Rules:

- Editing a published atom creates a draft revision.
- Draft revision can be reviewed and published.
- Publishing replaces the current searchable version.
- Old versions remain read-only.
- Reports store snapshots, so history does not change when atom content changes.

### 6.7 Unified Job Table

New table: `app_job`

Job types:

- `IMPORT_FILE`
- `REGENERATE_ATOMS`
- `REVIEW_ATOMS`
- `PUBLISH_ATOMS`
- `REINDEX_POSITION`
- `GENERATE_REPORT`

Core fields:

- `id`
- `job_type`
- `scope`
- `owner_user_id`
- `position_id`
- `knowledge_base_id`
- `source_file_id`
- `record_id`
- `status`: `PENDING` / `RUNNING` / `FAILED` / `COMPLETED`
- `stage`
- `progress`
- `payload_json`
- `result_json`
- `failed_stage`
- `error_message`
- `retryable`
- `retry_count`
- `claimed_by`
- `locked_until`
- `created_by`
- `create_time`
- `update_time`

Startup recovery:

- Re-enqueue `PENDING`.
- Re-enqueue `RUNNING` jobs whose lock expired.
- Do not auto-retry `FAILED`; user triggers retry.
- Handlers should be idempotent enough to survive retry.

### 6.8 Interview Turn And Report

Old interview/report data is cleared during migration.

Keep:

- Users.
- User LLM configs.
- Resume profiles.
- Feedback.
- Existing public question-bank content after migration.

Clear:

- Old interview records.
- Old RAG retrieval logs tied to old records.
- Old report-derived data.
- Old Mentor caches if they are based on old interview history.

New interview records must bind `position_id`.

Suggested new tables:

- `interview_turn`
- `interview_report`
- `interview_report_item`

Each report item stores a snapshot:

- question
- user answer
- phase: `TECHNICAL` / `HR`
- score, recommended scale 0-10
- reference answer or reference direction
- improvement suggestion
- answer source: `KNOWLEDGE_BASE` / `AI_GENERATED` / `HR_GUIDE`
- matched atom snapshot
- generated model/provider metadata
- generated time

Historical reports must not dynamically depend on the latest atom content.

## 7. File Storage

First version uses local mounted storage.

Default path example:

```text
data/uploads/knowledge-base/
```

The database stores metadata and storage keys only. Business code depends on a `FileStorageService` abstraction:

- `save`
- `read`
- `delete`
- `exists`
- `openStream`

First implementation: local filesystem.

Future implementation: MinIO, S3, Azure Blob, or another object storage.

This is important because the same codebase must support both local self-deployment and future cloud platform deployment.

## 8. Document Conversion Service

Add a Python `document-converter` service that wraps MarkItDown.

Responsibilities:

- Accept an uploaded file or internal file reference.
- Convert PDF, DOCX, Markdown, and TXT to Markdown.
- Return converted Markdown and conversion metadata.
- Produce clear errors for unsupported or failed conversion.

Spring Boot remains the orchestrator:

- Upload file.
- Save file metadata.
- Create job.
- Call document-converter.
- Store Markdown.
- Continue atom generation.

Do not call Python scripts directly with `ProcessBuilder` in the Java backend for the first version.

## 9. Import And Review Workflow

### 9.1 Automatic Preparation

After user uploads a supported file:

1. Save original file.
2. Convert to Markdown.
3. Generate atom drafts with the user's active LLM Provider.
4. Run LLM review on generated atoms.
5. Mark import as ready for user review.

### 9.2 LLM Review

LLM review checks:

- Atom covers one clear knowledge point.
- Atom is suitable for interview questioning.
- Reference answer is concrete enough.
- Atom is traceable to the source material.
- Atom is not obviously duplicated.
- Difficulty and tags are reasonable.
- No obvious hallucination.

Review output:

- `PASS`
- `NEEDS_REVIEW`
- `REJECT`
- reason
- suggested patch
- confidence

### 9.3 User Confirmation

Publication rules:

- `PASS`: can be published.
- `NEEDS_REVIEW`: publish only after accepting LLM patch or manual handling.
- `REJECT`: cannot be published in the first version.

Publishing:

- Set atom status to published.
- Generate embeddings.
- Upsert Qdrant points.
- Mark vector status.
- Position becomes interview-usable only after enough published atoms are `SYNCED`.

## 10. Qdrant Strategy

Use one shared collection, for example:

```text
interview_atoms_e5_base
```

Every point payload must include:

- `scope`
- `ownerUserId`
- `positionId`
- `knowledgeBaseId`
- `atomId`
- `status`

Interview retrieval must always use strict filters:

- Public position:
  - `scope = PUBLIC`
  - `positionId = selected public position`

- Private position:
  - `scope = PRIVATE`
  - `ownerUserId = current user`
  - `positionId = selected private position`

Do not perform unscoped interview retrieval.

No silent MySQL fallback in the interview path. If Qdrant is unavailable or the position has no synced atoms, block or fail with a clear message because retrieval quality is part of the product guarantee.

Management/debug search may have separate diagnostic behavior, but it must not change interview behavior.

## 11. Interview Flow Changes

### 11.1 Structured Position Selection

Interview setup changes from free text to structured position selection:

- Public positions.
- My positions.
- Status per position.

Statuses:

- usable
- no knowledge
- indexing
- index failed
- archived

Private positions cannot start interviews until they have published and synced atoms.

### 11.2 Turn Recording

The system should record structured turn data:

- interview record id
- turn index
- phase
- AI question
- user answer
- retrieved atom ids
- selected prompt context atom snapshots or summaries
- retrieval strategy
- answer source metadata
- timestamps

This should replace relying only on `interview_record.chat_history` for report generation.

## 12. Report Generation

End interview should create a `GENERATE_REPORT` job instead of blocking on full report generation.

UX:

- User ends interview.
- Backend returns record id and report job id.
- UI shows report generating.
- User can leave the page.
- History list shows generating, completed, or failed.
- Failed reports can be regenerated.
- Interview data is retained even if report generation fails.

Scoring:

- Score TECHNICAL and HR questions.
- Do not score OPENING or CLOSING turns as individual questions.
- Each scored item includes:
  - question
  - user answer
  - combined score
  - reference answer or reference direction
  - improvement suggestion
  - visible answer source

Reference source:

- Technical question with matched atom: source shown as knowledge base.
- Technical question without matched atom: source shown as AI-generated reference.
- HR question: source shown as AI-generated HR guidance/reference.

Recommended scoring scale:

- Per-question score: 0-10.
- Overall report score can remain 0-100.

## 13. Migration Strategy

This is a destructive migration for old interview/report data but not for users or question-bank content.

### 13.1 Preserve

- User accounts.
- User LLM configuration.
- Resume profile.
- Feedback.
- Existing public question-bank content after migration.

### 13.2 Clear

- Old interview records.
- Old RAG retrieval request logs and hit logs tied to interviews.
- Old report fields/data.
- Old Mentor persisted caches if they depend on old interview history.

### 13.3 Transform Existing Question Bank

Existing `knowledge_atom` records are migrated into the new model:

- `scope = PUBLIC`
- `owner_user_id = null`
- `position_id = one of the three public positions`
- `knowledge_base_id = public position default knowledge base`
- `publication_status = PUBLISHED` when old status is published
- `vector_status` migrated or recomputed

After migration, run a full reindex to ensure Qdrant payload contains the new scope fields.

## 14. Security And Privacy Requirements

- Ordinary users cannot read, edit, delete, or reindex other users' private content.
- Ordinary users cannot edit public positions or public atoms.
- Admin APIs require JWT plus admin role.
- Remove `APP_ADMIN_TOKEN` from the ordinary product flow.
- Uploaded file paths must not allow path traversal.
- File download/read endpoints must verify ownership or public read rules.
- LLM error messages must be sanitized and truncated.
- Logs must not print API keys, tokens, raw credentials, or sensitive headers.
- Qdrant retrieval must enforce filter construction in backend services, not through optional frontend input.

## 15. Rollout Phases

### Phase 1: Data Model And Migration

- Add role model.
- Add position, knowledge base, source file, job, turn, report tables.
- Migrate public question bank into three public positions.
- Clear old interview/report data.
- Add Qdrant payload model and reindex plan.

### Phase 2: User Knowledge Base Management

- Add sidebar page.
- Create private position.
- Upload file.
- Save original and Markdown.
- Add document-converter service.
- Add job polling.

### Phase 3: Atom Generation, Review, Publication

- Generate atom drafts through active Provider.
- Run LLM review.
- Show PASS/NEEDS_REVIEW/REJECT.
- Support manual create/edit atom.
- Publish eligible atoms.
- Embed and sync to Qdrant.

### Phase 4: RAG Interview Cutover

- Structured position selection.
- Block unavailable positions.
- Strict scoped Qdrant search.
- Structured turn recording.
- Remove silent MySQL fallback from interview path.

### Phase 5: Async Report

- Create report job on interview end.
- Generate report and per-question items.
- Show source for each reference answer.
- Add retry for failed report jobs.

### Phase 6: Admin Public Knowledge Management

- Admin role UI.
- Manage public positions and public knowledge bases.
- Grant/revoke admin.
- Public import pipeline using admin's active Provider.

## 16. Testing Strategy

Backend:

- Migration tests for public positions and atom scope.
- Permission tests for public/private/admin endpoints.
- Job lifecycle tests for retry and restart recovery.
- Qdrant filter construction tests.
- Report generation parser tests.
- File upload validation tests.

Frontend:

- Knowledge page state tests where feasible.
- Route/sidebar visibility for normal user and admin.
- Position usability guards on interview setup.
- Report generating/completed/failed states.

Integration:

- Upload Markdown -> generate atoms -> review -> publish -> Qdrant -> interview retrieval.
- Copy public position -> private indexed copy -> start private interview.
- End interview -> async report -> report item display with source.

Security review:

- Path traversal.
- Cross-user data access.
- Admin role checks.
- Secret redaction.
- Qdrant unscoped retrieval.

## 17. Main Risks

- LLM atom generation quality may vary across providers.
- LLM review can miss errors if generation and review use the same active model.
- Large documents may still exceed practical token limits even under file size limits.
- Migration and reindexing are high-risk and must be reversible or repeatable in local testing.
- Strict no-fallback RAG improves quality but creates more visible failures when Qdrant is unhealthy.
- Cloud platform readiness requires quota and storage controls beyond the first version.

## 18. Open Implementation Notes

- Keep import package contract concepts where useful, but move generation and publication into the application.
- Do not keep two parallel question-bank models longer than necessary.
- Keep old scripts/skills as developer utilities only if they still match the new schema.
- Update docs and README after implementation to explain public vs private positions.
- Treat this as a multi-agent L3/High-risk implementation because it touches database, RAG, file upload, LLM usage, frontend, and deployment.
