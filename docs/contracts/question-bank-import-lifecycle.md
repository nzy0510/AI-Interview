# Question Bank Import Lifecycle Contract

This contract keeps local package generation and the Java main app aligned around the same question-bank import lifecycle.

## Module

The Module is **Question Bank Import Lifecycle**:

1. validate an import package without writing atoms;
2. import atoms as `DRY_RUN`, `DRAFT`, or `AUTO_PUBLISH`;
3. synchronize published atoms to Qdrant;
4. reindex already-published atoms after embedding or vector-store changes.

## Interface

The generator and Java implementation read the same JSON package shape:

- `batchId`: optional stable import batch id.
- `sourceRef`: source material reference.
- `targetCategory`: default category for atoms that omit `category`.
- `mode`: `DRY_RUN`, `DRAFT`, or `AUTO_PUBLISH`.
- `atoms[].id`: stable knowledge atom id.
- `atoms[].subject`: interview topic.
- `atoms[].category`: atom category, or omitted when `targetCategory` is present.
- `atoms[].difficulty`: difficulty label.
- `atoms[].tags`: string tags.
- `atoms[].content.principles`: required standard answer / principles.
- `atoms[].content.pitfalls`: optional common mistakes.
- `atoms[].content.followUpPaths`: optional follow-up prompts.
- `atoms[].sourceRef`: optional atom-level source reference.

Canonical fixtures live in `question_bank_imports/fixtures/import-lifecycle/`.

## Implementations

- Java Adapter: `QuestionBankService` in the Spring Boot main app. It is used by the developer-only Question Bank Admin panel and the interview runtime.
- Package Generator: `scripts/question_bank_import.py` and `skills/interview-question-bank/` prepare JSON packages for human review and upload; they do not submit or publish content.

The generator must preserve the package schema expected by the Java adapter. The Java adapter owns persisted atom state, import result counts, and published-atom reindex semantics.

## Golden Behavior

- Empty packages fail with `atoms must not be empty`.
- Duplicate atom ids fail with `duplicate atom id in package: <id>`.
- Missing `content.principles` fails with `<id>: content.principles is required`.
- `DRY_RUN` writes an import batch only; it does not write atoms or Qdrant vectors.
- `DRAFT` writes atoms with `status=DRAFT` and `vectorStatus=SKIPPED`.
- `AUTO_PUBLISH` writes atoms with `status=PUBLISHED`, attempts Qdrant upsert, and marks successful vectors as `SYNCED`.
- Reindex only processes already-published atoms and returns the number successfully synced to Qdrant.

## Depth

The contract is intentionally small. It deepens the boundary where drift is most expensive: imported atom validity, persisted atom shape, and vector synchronization state. Broader client, UI, and authentication behavior stays in their own Modules.
