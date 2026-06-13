# User-Owned Question Bank Follow-Ups

Status: Future work backlog
Date: 2026-06-13
Related spec: `docs/superpowers/specs/2026-06-13-user-owned-question-bank-rag-report-design.md`

This document collects improvements intentionally deferred from the first redesign. They are not first-version requirements unless explicitly promoted into an implementation plan.

## 1. LLM Provider Routing

First version keeps one active Provider per user.

Future option:

- Let users assign different Providers to different tasks:
  - interviewer
  - atom generation
  - atom review
  - report generation
  - AI Mentor
- Add per-task override with default Provider fallback.
- Show cost and latency warnings by task.

Reason deferred:

- It adds configuration complexity before the new question-bank flow is stable.
- The current product just moved to user-managed Provider configuration; keep first version simple.

## 2. External Queue

First version uses MySQL `app_job` plus Spring `TaskExecutor`.

Future option:

- Redis Stream.
- RabbitMQ.
- Kafka only if the platform becomes large enough to justify it.

Recommended path:

1. Keep job state in MySQL.
2. Add queue adapter behind the job executor.
3. Use Docker Compose profiles for optional queue services.

Reason deferred:

- External queues increase self-deployment complexity.
- Current tasks need recoverable state more than strict once-only message delivery.

## 3. Object Storage

First version stores files in a local mounted volume through `FileStorageService`.

Future option:

- MinIO for self-hosted object storage.
- S3-compatible storage.
- Azure Blob for Azure deployments.

Required when:

- Cloud platform stores many user documents.
- Multiple backend instances need shared file access.
- Backups and retention policies become important.

## 4. Multiple Knowledge Bases Per Position

First version uses one default knowledge base per position.

Future option:

- A position can bind multiple knowledge bases.
- Users can enable or disable a knowledge base for interviews.
- Knowledge bases can have independent import/reindex status.

Reason deferred:

- First version already has positions, files, atoms, review, publication, indexing, jobs, and admin roles.
- Multiple knowledge bases would make UI and retrieval state more complex.

## 5. Interview-Time Tag Filtering

First version supports tags for file and atom management but does not expose tag filtering in interview setup.

Future option:

- Let users select focus tags before interview, such as `MySQL`, `Redis`, `Spring`.
- RAG filter combines position scope with selected tags.
- Report can summarize performance by selected tags.

Reason deferred:

- Need real usage data first.
- Position-level retrieval is simpler and less likely to confuse users.

## 6. More Import Formats

First version supports:

- PDF
- DOCX
- Markdown/MD
- TXT

Future import types:

- PPT/PPTX.
- Excel/CSV.
- Image OCR.
- Web URL crawling.
- Zip batch import.

Notes:

- OCR and web crawling introduce extra reliability and security work.
- URL crawling needs network controls, content-size limits, and robots/license consideration.

## 7. Stronger Atom Quality Pipeline

First version performs LLM generation plus LLM review.

Future improvements:

- Duplicate detection across the entire user knowledge base.
- Coverage report showing which document sections produced atoms.
- Source-span references from Markdown to atom.
- Confidence calibration across different providers.
- Optional second-review Provider when multi-provider routing is added.
- Human review sampling workflow for admins.

## 8. Quotas And Platform Limits

First version uses basic file and atom limits:

- 20 MB per file.
- 100 atoms per import.

Future platform quotas:

- Total storage per user.
- Daily import job count.
- Daily report generation count.
- Concurrent job limit.
- Token usage tracking per feature.
- Admin dashboard for slow/failing jobs.

Reason deferred:

- Billing and quota policy should follow actual deployment needs.

## 9. Team Or Organization Spaces

First version uses personal private positions only.

Future option:

- Organization-owned positions.
- Team-shared knowledge bases.
- Role permissions: owner, editor, viewer.
- Shared interview templates.

Reason deferred:

- Current target is individual self-hosted users and ordinary cloud platform users.

## 10. Public Marketplace Or Sharing

First version has public system positions plus private copies.

Future option:

- Users publish their own positions as templates.
- Admin review before platform-wide publication.
- Ratings/download count.
- Versioned public templates.

Risk:

- Needs moderation, abuse prevention, and copyright/content policy.

## 11. Advanced Report Analytics

First version generates per-question score, reference answer, and improvement suggestion.

Future options:

- Longitudinal trend per position.
- Knowledge-gap heatmap by tag.
- Compare repeated attempts on the same position.
- Export report as PDF/Markdown.
- Replay report with RAG evidence.

## 12. Deployment Profiles

First version should keep Docker Compose local-friendly.

Future profiles:

- `queue`: adds Redis Stream/RabbitMQ worker path.
- `object-storage`: adds MinIO.
- `cloud`: external DB, object storage, and scaled workers.
- `admin-tools`: optional public question-bank maintenance scripts.

## 13. Developer Tooling Cleanup

After the new in-app import flow is stable:

- Revisit `scripts/question_bank_import.py`.
- Revisit `.agents/skills/interview-question-bank`.
- Decide whether they remain developer utilities, migrate to the new schema, or are retired.
- Update ADRs and contracts to remove admin-token workflow language.

## 14. Non-MVP Safety Enhancements

- Virus/malware scanning for uploads.
- Per-file content classification warnings.
- PII detection in uploaded documents.
- User-visible delete/export data controls.
- Audit log for admin actions.
- Admin approval workflow for public question-bank changes.

## 15. Promotion Criteria

A follow-up should be promoted into an implementation plan only when at least one is true:

- It removes a real blocker found during MVP implementation.
- It is needed for cloud platform launch.
- It reduces verified support burden.
- It protects security, privacy, or tenant isolation.
- User testing shows the MVP workflow is not usable without it.
