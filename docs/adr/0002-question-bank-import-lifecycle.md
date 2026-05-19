# Question Bank Import Lifecycle

Status: accepted

InterWise imports new question-bank content through a fixed lifecycle: source material becomes an import package, the package is validated, validated content becomes knowledge atoms, and only published atoms are reindexed for retrieval. This keeps MySQL as the business source of truth, keeps Qdrant rebuildable as a retrieval index, and avoids direct database or vector-store edits during maintenance.

The executable contract lives in `docs/contracts/question-bank-import-lifecycle.md` and `question_bank_imports/fixtures/import-lifecycle/`. Java and standalone MCP tests read the same fixtures so the two adapters preserve the same validation errors, normalized atom fields, import result counts, and Qdrant synchronization semantics.
