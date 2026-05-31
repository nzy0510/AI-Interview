---
name: interview-question-bank
description: Maintain the AI Interview project question bank from Codex. Use when the user asks to add, generate, review, import, publish, reindex, or inspect interview knowledge atoms; convert PDF, DOCX, TXT, MD, or JSON materials into question-bank import packages; call the project question-bank API or MCP tools; or update categories for new roles in E:\Java-web\interview.
---

# Interview Question Bank

Use this skill to maintain the private developer-owned question bank for the AI Interview project. The project stores atoms in MySQL, syncs published atoms to Qdrant, exposes internal maintenance APIs, and exposes an MCP JSON-RPC endpoint for external AI clients.

## Workflow

1. Inspect the requested source and target category. For a new role or category, also check `backend/src/main/resources/application.yml.example` and the live config for `interview.position-categories`.
2. Convert source files with `scripts/question_bank_import.py` when inputs are PDF, DOCX, TXT, MD, or JSON.
3. Review the generated package before publishing unless the user explicitly asks for direct update. Use `DRAFT` for staged imports and `AUTO_PUBLISH` for direct live use.
4. Submit through `POST /internal/question-bank/import` or through the MCP tool `submit_atom_import_package`. Do not write question-bank tables directly.
5. Reindex with `POST /internal/question-bank/reindex` or MCP tool `reindex_question_bank` after bulk edits when automatic indexing was disabled or failed.
6. Verify by searching through `/internal/question-bank/search` or MCP tool `search_interview_atoms`.

## Commands

Generate an import package from documents:

```powershell
python scripts/question_bank_import.py --input .\materials\java --category java --mode DRAFT
```

Generate and submit directly:

```powershell
$env:QUESTION_BANK_ADMIN_TOKEN="your-token"
python scripts/question_bank_import.py --input .\materials\redis.pdf --category redis --mode AUTO_PUBLISH --submit
```

Normalize existing JSON atoms:

```powershell
python scripts/question_bank_import.py --input .\atoms.json --category mysql --mode DRAFT
```

## Mode Choice

- `DRY_RUN`: validate an import package shape without writing atoms.
- `DRAFT`: write atoms to MySQL as drafts; they are not used by the interviewer until published later.
- `AUTO_PUBLISH`: write atoms as published and immediately sync them to Qdrant; use this only when the user has approved direct updates.

## References

- Read `references/atom-schema.md` before editing import package structure.
- Read `references/mcp-tools.md` before wiring an external AI client or explaining MCP tool behavior.
- Read `references/review-rubric.md` before reviewing generated atoms for quality.
