# InterWise RAG Retrieval Evaluation Design

## 1. Purpose

InterWise currently retrieves published AI-model interview knowledge atoms through Qdrant vector similarity, category filtering, and used-atom exclusion, then injects the returned Top-3 atoms into the interview prompt.

This design establishes a repeatable retrieval evaluation system before introducing a reranker. It is intended to answer:

1. Whether the current `AllMiniLmL6V2` embedding model is suitable for Chinese AI-model interview queries.
2. Whether a larger candidate set such as Top-10 or Top-20 contains substantially more useful follow-up atoms than the current Top-3.
3. Whether a multilingual or stronger embedding model improves retrieval enough to justify production migration and Qdrant reindexing.
4. Whether future reranking work is likely to improve final Top-3 ordering.

The first evaluation scope is limited to the **AI 大模型** technical interview category.

## 2. Current Retrieval Boundary

The production query is:

```text
previous AI interviewer question + current candidate answer
```

The current runtime flow is:

```text
InterviewServiceImpl
  -> build QuestionBankSearchRequest
  -> QuestionBankService.search
  -> QdrantVectorService.search
  -> load published atoms from MySQL
  -> inject Top-3 atom prompt contexts into InterviewTurnPlanner
```

Qdrant returns atoms ordered by vector similarity. The current runtime does not apply a separate second-stage reranker.

MySQL remains the business source of truth for knowledge atoms. Qdrant remains a rebuildable retrieval index.

## 3. Scope

### In Scope

- Build a versioned 100-query AI-model retrieval evaluation set.
- Prefer anonymized real interview queries and use reviewed synthetic queries to fill the remaining quota.
- Add request-level retrieval logging so zero-hit queries are observable.
- Compare candidate-set sizes Top-3, Top-10, Top-20, and Top-30.
- Compare three embedding models offline.
- Build pooled query-atom judgments with 0-3 graded relevance.
- Calculate repeatable retrieval metrics and decision thresholds.

### Out of Scope

- Adding a production reranker.
- Replacing the production embedding model.
- Migrating or reindexing the production Qdrant collection.
- Expanding evaluation to Java, frontend, or HR soft-skill categories.
- Automatically publishing or editing knowledge atoms.
- Storing raw private interview records in the repository.

## 4. Query And Atom Relationship

The evaluation set contains 100 retrieval queries. The AI-model question bank contains approximately 120 published atoms.

Each query searches the shared atom corpus:

```text
100 queries
  -> each query searches approximately 120 AI-model atoms
  -> each search returns a ranked Top-K result list
```

The relationship is many-to-many:

- One query may have several relevant atoms.
- One atom may be relevant to several different queries.
- A query does not contain atom content.
- A judgment records whether one atom is suitable for one query.

The theoretical full-labeling cost is:

```text
100 queries x 120 atoms = 12,000 query-atom judgments
```

This design does not require full labeling. It uses pooled candidates to reduce review effort while preserving useful coverage.

## 5. Evaluation Set

### 5.1 Dataset Identity

The first fixed dataset is:

```text
retrieval-eval-ai-model-v1
```

It contains exactly 100 reviewed queries. Once finalized, v1 is immutable. Future real-query growth produces a new version rather than silently changing v1.

### 5.2 Query Sources

Use sources in this order:

1. All currently available valid anonymized real AI-model technical interview queries, up to 40 queries.
2. Reviewed synthetic queries generated from published AI-model atoms to fill the dataset to 100 queries.

Examples:

```text
8 valid real queries + 92 reviewed synthetic queries = 100 queries
40 valid real queries + 60 reviewed synthetic queries = 100 queries
```

Real queries are preferred because they preserve candidate language, incomplete answers, misconceptions, mixed terminology, and noisy expression.

### 5.3 Valid Real Query Definition

A real query is valid when all of the following are true:

- The interview position maps to the `AI大模型` category.
- The turn belongs to the technical interview stage.
- A previous AI interviewer question and current candidate answer are available.
- The combined context has an identifiable technical topic or knowledge gap.
- The query remains meaningful after anonymization.
- It is not a duplicate or near-duplicate of another selected query.
- It is not a system operation, opening introduction, HR question, closing message, or unrelated chat.

Short answers are not automatically invalid. For example, “我不太清楚 LoRA 为什么能减少参数量” is still useful because the technical topic is explicit.

### 5.4 Synthetic Query Coverage

Synthetic queries fill missing coverage after real-query selection. Their target scenario distribution is:

| Scenario | Target Count |
| --- | ---: |
| Correct but incomplete answer suitable for deeper follow-up | 25 |
| Concept confusion or incorrect answer | 20 |
| Basic answer suitable for easier follow-up | 15 |
| Multiple technical topics mixed together | 15 |
| Colloquial Chinese, mixed Chinese-English terms, or typos | 10 |
| Short answer with a clear technical topic | 10 |
| Off-topic or low-relevance noise | 5 |

Real queries reduce the synthetic quota for the matching scenario. The final dataset remains balanced across scenarios rather than merely sampling the most common topic.

### 5.5 Privacy Rules

Only anonymized derived evaluation data may be committed.

Remove or replace:

- user IDs and record IDs
- names, emails, phone numbers, addresses, and account identifiers
- company names or confidential project details
- full raw interview histories
- tokens, secrets, and internal URLs

The committed dataset must not be reversible back to a specific user or interview record.

## 6. Dataset Format

Store the fixed evaluation set under:

```text
backend/src/test/resources/retrieval-eval/
  ai-model-v1.jsonl
  ai-model-v1-metadata.json
```

Each JSONL row represents one query:

```json
{
  "query_id": "ai-model-v1-0001",
  "position": "AI大模型",
  "phase": "TECHNICAL",
  "source": "real_anonymized",
  "scenario": "correct_but_incomplete",
  "previous_ai_question": "为什么自注意力计算要除以根号 dk？",
  "candidate_answer": "主要是为了避免数值太大，但具体影响我不太清楚。",
  "query_text": "为什么自注意力计算要除以根号 dk？ 主要是为了避免数值太大，但具体影响我不太清楚。",
  "judgments": [
    {
      "atom_id": "scaled-dot-product-attention",
      "relevance": 3,
      "reason": "直接对应候选人回答中暴露的知识缺口"
    },
    {
      "atom_id": "multi-head-attention",
      "relevance": 1,
      "reason": "主题相关，但不是当前最佳追问"
    }
  ]
}
```

`query_text` must match the production concatenation behavior. The dataset stores query-atom relevance, not vector similarity scores.

## 7. Relevance Judgment

The evaluation question is:

> Is this knowledge atom suitable as the basis for the next interviewer follow-up after this candidate answer?

This is different from asking whether the query and atom are textually similar.

| Relevance | Definition |
| ---: | --- |
| 3 | Highly suitable for a direct next follow-up |
| 2 | Relevant and usable, but not the best follow-up |
| 1 | Topically related, but unsuitable for the current follow-up context |
| 0 | Irrelevant, misleading, or likely to cause an abrupt topic jump |

Knowledge atom quality and query-atom relevance are separate:

- Question-bank review ensures that an atom is correct and useful in general.
- Retrieval evaluation determines whether that atom is useful for a specific query.

## 8. Candidate Pooling And Annotation

### 8.1 Why Pooling

Full manual labeling of every query against every atom is unnecessary and expensive. Instead, build a candidate pool for each query from multiple retrieval methods.

### 8.2 Candidate Pool Sources

For each query, collect:

- Current `AllMiniLmL6V2` Top-20
- `multilingual-e5-base` Top-20
- `BAAI/bge-m3` Top-20
- Keyword retrieval Top-20
- A small random sample of atoms outside all Top-20 lists

Merge and deduplicate the results. The expected pool size is approximately 25-40 atoms per query.

### 8.3 Annotation Workflow

1. A language model pre-labels each pooled query-atom pair with relevance `0-3` and a one-sentence reason.
2. A human reviewer checks every candidate labeled `2` or `3`.
3. A human reviewer checks candidates with large ranking disagreement between retrieval methods.
4. A human reviewer checks low-confidence model judgments.
5. A human reviewer randomly samples `0` and `1` judgments.
6. A human reviewer samples atoms outside the pool to estimate whether pooling missed relevant atoms.
7. Final reviewed judgments are written into the immutable v1 dataset.

The model may assist annotation but is not the source of truth. Human-reviewed judgments are the evaluation ground truth.

## 9. Embedding Model Comparison

The first evaluation compares exactly three models:

| Model | Role | Vector Dimension |
| --- | --- | ---: |
| `AllMiniLmL6V2` | Current production baseline | 384 |
| `multilingual-e5-base` | Chinese and multilingual candidate | 768 |
| `BAAI/bge-m3` | Stronger but more resource-intensive candidate | 1024 |

The comparison is offline. It does not modify production code, production Qdrant collections, or published atoms.

Model-specific encoding requirements must be respected. In particular, `multilingual-e5-base` uses:

```text
query: <query text>
passage: <atom text>
```

Each model embeds the same query set and the same published AI-model atom corpus. Similarity is calculated consistently for that model, and ranked Top-K lists are written as evaluation artifacts.

## 10. Candidate-Set Experiment

For each embedding model, calculate results at:

```text
Top-3
Top-10
Top-20
Top-30
```

Top-3 represents the current final prompt context size. Top-10, Top-20, and Top-30 reveal how much useful material exists outside the current result set and whether a future reranker has room to improve ordering.

Expanding the candidate set alone does not improve production output if the final prompt still receives the original vector Top-3. Candidate expansion is preparation for evaluation and future reranking.

## 11. Metrics

Treat judgments with relevance `>= 2` as relevant for recall and hit-rate metrics.

### 11.1 Recall@K

```text
Recall@K = relevant atoms in Top-K / all judged relevant atoms for the query
```

Recall measures whether the candidate set contains useful follow-up atoms.

### 11.2 HitRate@K

```text
HitRate@K = percentage of queries with at least one relevance >= 2 atom in Top-K
```

HitRate reflects whether the interviewer has at least one usable follow-up basis.

### 11.3 NDCG@3

NDCG@3 uses the graded `0-3` labels and measures whether higher-quality follow-up atoms appear earlier in the final Top-3.

### 11.4 MRR

MRR measures the first position at which a relevance `3` atom appears. If no relevance `3` atom exists for a query, use the first relevance `2` atom according to the evaluation implementation rules recorded in metadata.

### 11.5 Zero-Hit Rate

Zero-hit rate measures the percentage of queries with no relevance `>= 2` atom in the evaluated candidate set.

### 11.6 Diversity@3

Diversity@3 identifies whether the final Top-3 is dominated by near-duplicate or same-concept atoms. The first version may use atom subject/category review rather than automated semantic clustering.

## 12. Deciding Whether Top-20 Is Meaningfully Better Than Top-3

Define:

```text
deltaRecall = mean Recall@20 - mean Recall@3
```

Treat Recall@20 as meaningfully better than Recall@3 only when all conditions hold:

1. Mean `deltaRecall >= 0.10`.
2. At least 20% of queries have `Recall@20 > Recall@3`.
3. `HitRate@20` exceeds `HitRate@3` by at least 5 percentage points.
4. The lower bound of the paired bootstrap 95% confidence interval for `deltaRecall` is greater than 0.

Interpretation:

- High Recall@20 with low NDCG@3 indicates a ranking problem and supports future reranking.
- Low Recall@20 indicates a recall problem; improve embeddings, query construction, atom text, or filters before reranking.
- High Recall@3 and high NDCG@3 indicate limited reranking value.

## 13. Request-Level Retrieval Logging

### 13.1 Problem

The existing `rag_retrieval_log` records one row per retrieved atom. A query that returns zero atoms creates no row, so difficult zero-hit queries are invisible and cannot be sampled for evaluation.

### 13.2 New Request Log

Add a request-level table:

```text
rag_retrieval_request_log
```

One row represents one retrieval attempt, including successful zero-hit attempts.

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | Request log primary key |
| `request_id` | Stable identifier shared with hit rows |
| `user_id` | Restricted operational ownership field |
| `record_id` | Interview record association |
| `turn_index` | Interview turn |
| `position` | Interview position |
| `phase` | Interview phase |
| `query_text` | Production retrieval query, access-restricted |
| `requested_limit` | Requested result limit |
| `candidate_count` | Returned candidate count, including zero |
| `retrieval_strategy` | For example `QDRANT_VECTOR` or `MYSQL_FALLBACK` |
| `latency_ms` | Retrieval latency |
| `status` | `SUCCESS` or `FAILED` |
| `error_message` | Sanitized failure message |
| `create_time` | Timestamp |

Add `request_id` to the existing `rag_retrieval_log` so hit rows can be linked to their request.

### 13.3 Privacy And Access

`query_text` may contain personal or confidential information. Production access must remain restricted. Repository evaluation data must be produced through an explicit anonymization and review step.

Logs must not contain secrets, tokens, passwords, or complete unrelated interview histories.

## 14. Proposed Repository Artifacts

```text
backend/src/test/resources/retrieval-eval/
  ai-model-v1.jsonl
  ai-model-v1-metadata.json

scripts/retrieval_eval/
  extract_real_queries.py
  generate_synthetic_queries.py
  build_candidate_pool.py
  score_embeddings.py
  calculate_metrics.py
```

Expected generated artifacts should be written outside committed source data unless explicitly selected for review:

```text
output/retrieval-eval/
  ai-model-v1-candidate-pool.jsonl
  ai-model-v1-model-rankings.json
  ai-model-v1-metrics.json
  ai-model-v1-report.md
```

## 15. Testing Strategy

### Request Logging

- A successful retrieval with hits creates one request row and linked hit rows.
- A successful zero-hit retrieval creates one request row and no hit rows.
- A failed retrieval creates one failed request row with a sanitized error message.
- Existing interview streaming continues when retrieval fails.

### Evaluation Scripts

- Query concatenation matches production behavior.
- Anonymization removes configured sensitive patterns.
- Candidate pooling deduplicates atom IDs.
- Metrics are correct for fixed small fixtures.
- Bootstrap output is deterministic when a random seed is supplied.
- Model-specific query and passage formatting is applied correctly.

### Dataset Validation

- Exactly 100 unique query IDs exist.
- Every judgment references an existing published AI-model atom.
- Every relevance score is an integer from 0 to 3.
- No committed row contains forbidden identifiers or obvious personal data.
- Scenario and source distributions match metadata.

## 16. Decision Outputs

The evaluation report must provide:

- Metrics per embedding model at Top-3, Top-10, Top-20, and Top-30.
- Paired differences between the current model and each candidate model.
- Paired bootstrap confidence intervals.
- Query-level examples where Top-20 succeeds but Top-3 fails.
- Query-level examples where all models fail.
- Recommendation: keep current embedding, migrate embedding, improve retrieval inputs, or proceed to reranking design.

No production model migration or reranking implementation proceeds solely from aggregate metrics. Review representative failure examples before making the final decision.

## 17. Success Criteria

This phase is complete when:

- A reviewed, anonymized, immutable 100-query AI-model evaluation dataset exists.
- Request-level retrieval logging captures zero-hit queries.
- The three embedding models have been compared offline.
- Candidate-set metrics and confidence intervals are reproducible.
- The report provides evidence for or against embedding migration and future reranking.
