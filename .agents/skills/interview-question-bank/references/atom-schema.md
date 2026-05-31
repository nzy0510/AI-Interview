# Knowledge Atom Schema

Use this schema for `QuestionBankImportRequest.atoms`.

```json
{
  "id": "java-jvm-gc-roots-001",
  "subject": "GC Roots and reachability analysis",
  "category": "java",
  "difficulty": "mid",
  "tags": ["jvm", "gc"],
  "sourceRef": "jvm-notes.pdf",
  "content": {
    "principles": "Core principles and expected answer.",
    "pitfalls": "Common mistakes or interviewer traps.",
    "followUpPaths": [
      "Ask how weak references affect reachability.",
      "Ask how to diagnose an unexpected Full GC."
    ]
  }
}
```

Rules:

- `id` must be stable. Prefer lowercase English slugs with a numeric suffix for generated batches.
- `category` must match a question-bank category used by `interview.position-categories`.
- `principles` is the retrieval text that the interviewer sees as core answer context.
- `pitfalls` should be text, not an array. Convert old arrays by joining them with newlines.
- `followUpPaths` should be camelCase in new import packages. The legacy JSON loader also accepts `follow_up_paths`.
- Use `sourceRef` to preserve traceability to the source document or generation batch.
