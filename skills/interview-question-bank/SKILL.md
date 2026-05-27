---
name: interview-question-bank
description: Generate and review AI Interview question-bank import packages from Codex. Use when the user asks to prepare PDF, DOCX, TXT, MD, or JSON materials for import, review generated knowledge atoms, or update categories for new roles in E:\Develop\interview.
---

# Interview Question Bank

Use this skill to prepare import packages for the private developer-owned question bank. The project stores atoms in MySQL and syncs published atoms to Qdrant. Publication is performed only from the developer-only Question Bank Admin panel in the web application.

## Workflow

1. Inspect the requested source and target category. For a new role or category, also check `backend/src/main/resources/application.yml.example` and the live config for `interview.position-categories`.
2. Convert source files with `scripts/question_bank_import.py` when inputs are PDF, DOCX, TXT, MD, or JSON.
3. Review the generated package before handing it to the maintainer. Use `DRAFT` for staged imports and `AUTO_PUBLISH` only when the maintainer intends immediate publication.
4. In the web application, sign in with a developer account and open `Settings -> Question Bank Admin`.
5. Enter `APP_ADMIN_TOKEN`, upload the generated JSON package, validate it, perform a dry run, and explicitly publish it.
6. Verify the published atoms and reindex status from the Question Bank Admin panel.

## Commands

Generate an import package from documents:

```powershell
python scripts/question_bank_import.py --input .\materials\java --category java --mode DRAFT
```

Normalize existing JSON atoms:

```powershell
python scripts/question_bank_import.py --input .\atoms.json --category mysql --mode DRAFT
```

## Mode Choice

- `DRY_RUN`: prepare a package intended for validation only.
- `DRAFT`: prepare atoms to be imported as drafts; they are not used by the interviewer until published later.
- `AUTO_PUBLISH`: prepare atoms for immediate publication and Qdrant sync; use only when the maintainer has approved direct publication in the admin panel.

## References

- Read `references/atom-schema.md` before editing import package structure.
- Read `references/review-rubric.md` before reviewing generated atoms for quality.
