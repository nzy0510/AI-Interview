# Question Bank Import Lifecycle Contract

This contract keeps the Java main app and the standalone MCP-Skill service aligned around the same question-bank import lifecycle.

## Module

The Module is **Question Bank Import Lifecycle**:

1. validate an import package without writing atoms;
2. import atoms as `DRY_RUN`, `DRAFT`, or `AUTO_PUBLISH`;
3. synchronize published atoms to Qdrant;
4. reindex already-published atoms after embedding or vector-store changes.

## Interface

Both Implementations read the same JSON package shape:

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

- Java Adapter: `QuestionBankService` in the Spring Boot main app. It is used by internal REST APIs and the interview runtime.
- Python Adapter: `services/mcp-skill/mcp_server/question_bank.py` in the standalone MCP service. It is used by public `/mcp` and admin `/mcp-admin` tools.

The two Adapters are allowed to use different storage code, but they must preserve the same validation errors, normalized atom fields, import result counts, and published-atom reindex semantics.

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
