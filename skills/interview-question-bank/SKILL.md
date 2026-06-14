---
name: interview-question-bank
description: Generate and review AI Interview question-bank import packages from Codex. Use when the user asks to prepare PDF, DOCX, TXT, MD, or JSON materials for import, review generated knowledge atoms, or update categories for new roles in E:\Develop\interview.
---

# Interview Question Bank

Use this skill to prepare reviewable question-bank materials for InterWise. The project stores atoms in MySQL and syncs published atoms to Qdrant. Normal publication happens through the application with user ownership or `ADMIN` role checks; legacy JSON import packages are developer tooling artifacts and must not bypass product authorization.

## Workflow

1. Inspect the requested source and target category. For a new role or category, also check `backend/src/main/resources/application.yml.example` and the live config for `interview.position-categories`.
2. Convert source files with `scripts/question_bank_import.py` when inputs are PDF, DOCX, TXT, MD, or JSON.
3. Review the generated package before handing it to the maintainer. Use `DRAFT` for staged imports and `AUTO_PUBLISH` only when the maintainer intends immediate publication.
4. In the web application, sign in and use the `知识库 / 题库` workspace for normal user-owned imports, review, publication, and indexing.
5. If a legacy JSON package must be used as a developer tool, first confirm its schema matches the current user-owned question-bank model, then run it through an authenticated `ADMIN`-guarded maintenance path.
6. Verify the published atoms and reindex status from the knowledge workspace or the relevant administrator maintenance surface.

## Commands

Generate an import package from documents:

```powershell
python scripts/question_bank_import.py --input .\materials\java --category java --mode DRAFT
```

Normalize existing JSON atoms:

```powershell
python scripts/question_bank_import.py --input .\atoms.json --category mysql --mode DRAFT
```

Default package filenames use `question_bank_imports/qb-<category>-<mode>-<YYYYMMDD-HHMMSS>-<shortid>.json`. Use `--output` only when the maintainer needs an explicit filename. Prefer application-native file upload for new user-owned question-bank flows.

## Mode Choice

- `DRY_RUN`: prepare a package intended for validation only.
- `DRAFT`: prepare atoms to be imported as drafts; they are not used by the interviewer until published later.
- `AUTO_PUBLISH`: prepare atoms for immediate publication and Qdrant sync; use only when the maintainer has approved direct publication in the admin panel.

## References

- Read `references/atom-schema.md` before editing import package structure.
- Read `references/review-rubric.md` before reviewing generated atoms for quality.
