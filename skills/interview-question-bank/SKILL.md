---
name: interview-question-bank
description: Generate and review InterWise question-bank import packages from Codex. Use when the user asks to prepare PDF, DOCX, TXT, MD, or JSON materials for import into a private position knowledge base, or review generated knowledge atoms.
---

# Interview Question Bank

Use this skill to prepare local JSON import packages for a user-owned position knowledge base. The application imports packages from the `知识库 / 题库` panel into the selected private position as draft atoms; publication and Qdrant sync are performed explicitly from that panel after review.

## Workflow

1. Inspect the requested source and target role/category. Confirm which private position knowledge base the package is for.
2. Convert source files with `scripts/question_bank_import.py` when inputs are PDF, DOCX, TXT, MD, or JSON.
3. Review the generated package before handing it to the user. Use `DRAFT` for normal imports. Do not rely on package-level auto publication for user-owned knowledge bases.
4. In the web application, sign in as the owning user and open `知识库 / 题库`.
5. Select the private position, upload the generated JSON package in `导入包维护`, validate it, then import it as draft.
6. Review the imported atoms in the same panel, then publish, archive, or rebuild vector indexes explicitly.

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
- `AUTO_PUBLISH`: legacy package mode for global maintenance only. The user-owned knowledge-base import path forces package imports to draft and requires explicit publication from the web panel.

## References

- Read `references/atom-schema.md` before editing import package structure.
- Read `references/review-rubric.md` before reviewing generated atoms for quality.
