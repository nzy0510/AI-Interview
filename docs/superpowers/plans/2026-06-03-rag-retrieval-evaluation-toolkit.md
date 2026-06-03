# RAG Retrieval Evaluation Toolkit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible offline toolkit and versioned AI-model retrieval evaluation dataset that compares candidate-set sizes and embedding models without modifying production Qdrant.

**Architecture:** Use read-only MySQL exports for anonymized real queries and published AI-model atoms, reviewed synthetic queries to reach 100 queries, pooled candidates from three embedding models plus keyword retrieval, model-assisted pre-labeling with human review, and deterministic metric calculation. Keep heavyweight model dependencies isolated under `scripts/retrieval_eval`.

**Tech Stack:** Python 3.11+, `unittest`, PyMySQL, sentence-transformers, FlagEmbedding, NumPy, scikit-learn, OpenAI-compatible chat API, JSONL

---

## Execution Dependency

Complete `2026-06-03-rag-retrieval-request-logging.md` first. The evaluation toolkit can read historical hit-level logs, but request-level logs are required to capture zero-hit queries without bias.

## File Structure

**Create**

- `scripts/retrieval_eval/__init__.py`
  Marks the toolkit as an importable package.
- `scripts/retrieval_eval/requirements.txt`
  Isolates optional offline evaluation dependencies.
- `scripts/retrieval_eval/common.py`
  JSONL I/O, atom text construction, anonymization, and shared constants.
- `scripts/retrieval_eval/db.py`
  Read-only MySQL connection helpers using dedicated environment variables.
- `scripts/retrieval_eval/export_atoms.py`
  Exports published `AI大模型` atoms to a local JSONL corpus.
- `scripts/retrieval_eval/extract_real_queries.py`
  Exports, anonymizes, filters, and deduplicates real retrieval queries.
- `scripts/retrieval_eval/generate_synthetic_queries.py`
  Uses an OpenAI-compatible chat API to fill scenario quotas.
- `scripts/retrieval_eval/score_embeddings.py`
  Produces ranked results for the three embedding models.
- `scripts/retrieval_eval/build_candidate_pool.py`
  Merges embedding, keyword, and random-negative candidates.
- `scripts/retrieval_eval/prelabel_candidates.py`
  Produces model-assisted `0-3` relevance suggestions for human review.
- `scripts/retrieval_eval/calculate_metrics.py`
  Calculates Recall, HitRate, NDCG, MRR, zero-hit rate, and paired bootstrap intervals.
- `scripts/retrieval_eval/validate_dataset.py`
  Enforces v1 dataset shape, privacy checks, atom references, and distributions.
- `tests/test_retrieval_eval_common.py`
- `tests/test_retrieval_eval_pool.py`
- `tests/test_retrieval_eval_metrics.py`
- `tests/test_retrieval_eval_dataset.py`
- `backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl`
- `backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl`
- `backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json`

**Modify**

- `.gitignore`
  Ignores generated model downloads, raw exports, and unreviewed output artifacts.
- `README.md`
  Documents the evaluation workflow and commands.
- `CHANGELOG.md`
  Records the offline evaluation capability.
- `docs/superpowers/specs/2026-06-03-rag-retrieval-evaluation-design.md`
  Records the fixed Atom snapshot and `prelabel_candidates.py` artifacts.

## Task 1: Scaffold The Evaluation Package And Shared Utilities

**Files:**
- Create: `scripts/retrieval_eval/__init__.py`
- Create: `scripts/retrieval_eval/requirements.txt`
- Create: `scripts/retrieval_eval/common.py`
- Create: `tests/test_retrieval_eval_common.py`
- Modify: `.gitignore`

- [ ] **Step 1: Write failing shared utility tests**

```python
import unittest

from scripts.retrieval_eval.common import anonymize_text, build_atom_text, dedupe_queries


class RetrievalEvalCommonTest(unittest.TestCase):
    def test_anonymize_text_removes_common_personal_identifiers(self):
        raw = "我叫张三，邮箱 zhangsan@example.com，电话 13800138000。"
        cleaned = anonymize_text(raw)
        self.assertNotIn("张三", cleaned)
        self.assertNotIn("zhangsan@example.com", cleaned)
        self.assertNotIn("13800138000", cleaned)

    def test_build_atom_text_matches_production_search_content(self):
        atom = {
            "subject": "注意力机制",
            "principles": "核心原理",
            "pitfalls": "常见错误",
            "follow_up_paths": ["追问一", "追问二"],
        }
        text = build_atom_text(atom)
        self.assertIn("考核点: 注意力机制", text)
        self.assertIn("核心原理与标准答案: 核心原理", text)
        self.assertIn("推荐的深度追问路径", text)

    def test_dedupe_queries_keeps_first_normalized_query(self):
        rows = [
            {"query_text": "什么是 RAG？"},
            {"query_text": "  什么是  RAG？ "},
        ]
        self.assertEqual(len(dedupe_queries(rows)), 1)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```powershell
python -m unittest tests.test_retrieval_eval_common -v
```

Expected: FAIL because `scripts.retrieval_eval.common` does not exist.

- [ ] **Step 3: Add optional dependency requirements**

```text
pymysql==1.1.1
numpy==1.26.4
scikit-learn==1.5.2
sentence-transformers==3.2.1
FlagEmbedding==1.3.3
```

- [ ] **Step 4: Implement shared utilities**

```python
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Iterable


AI_MODEL_CATEGORY = "AI大模型"
RELEVANT_THRESHOLD = 2

EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
NAME_RE = re.compile(r"(?:我叫|我是|姓名是)\s*[\u4e00-\u9fff]{2,4}")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def anonymize_text(value: str) -> str:
    text = EMAIL_RE.sub("[EMAIL]", value or "")
    text = PHONE_RE.sub("[PHONE]", text)
    text = NAME_RE.sub("[NAME]", text)
    return text.strip()


def normalize_query(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip().lower()


def dedupe_queries(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    result: list[dict[str, Any]] = []
    for row in rows:
        key = normalize_query(str(row.get("query_text", "")))
        if key and key not in seen:
            seen.add(key)
            result.append(row)
    return result


def build_atom_text(atom: dict[str, Any]) -> str:
    follow = atom.get("follow_up_paths") or []
    if isinstance(follow, str):
        follow_text = follow
    else:
        follow_text = json.dumps(follow, ensure_ascii=False)
    return (
        f"考核点: {atom.get('subject', '')}\n"
        f"核心原理与标准答案: {atom.get('principles', '')}\n"
        f"面试常见陷阱与候选人易错点: {atom.get('pitfalls', '')}\n"
        f"推荐的深度追问路径: {follow_text}"
    )
```

- [ ] **Step 5: Ignore raw and generated artifacts**

Add:

```gitignore
# Retrieval evaluation raw and generated artifacts
output/retrieval-eval/
.cache/retrieval-eval/
scripts/retrieval_eval/.env
```

- [ ] **Step 6: Run shared utility tests**

```powershell
python -m unittest tests.test_retrieval_eval_common -v
```

Expected: PASS.

- [ ] **Step 7: Commit toolkit scaffold**

```bash
git add scripts/retrieval_eval tests/test_retrieval_eval_common.py .gitignore
git commit -m "feat: scaffold retrieval evaluation toolkit"
```

## Task 2: Add Read-Only Database Exporters

**Files:**
- Create: `scripts/retrieval_eval/db.py`
- Create: `scripts/retrieval_eval/export_atoms.py`
- Create: `scripts/retrieval_eval/extract_real_queries.py`
- Modify: `tests/test_retrieval_eval_common.py`

- [ ] **Step 1: Add failing real-query filtering tests**

Append:

```python
from scripts.retrieval_eval.common import is_valid_real_query


def test_valid_real_query_requires_ai_model_technical_context(self):
    valid = {
        "position": "AI大模型",
        "phase": "TECHNICAL",
        "query_text": "LoRA 为什么可以减少参数量？ 我只知道它会训练低秩矩阵。",
    }
    invalid = {
        "position": "Java 后端开发",
        "phase": "TECHNICAL",
        "query_text": "LoRA 为什么可以减少参数量？ 我只知道它会训练低秩矩阵。",
    }
    assert is_valid_real_query(valid)
    assert not is_valid_real_query(invalid)
```

- [ ] **Step 2: Implement the query validator**

Add to `common.py`:

```python
def is_valid_real_query(row: dict[str, Any]) -> bool:
    return (
        AI_MODEL_CATEGORY in str(row.get("position", ""))
        and str(row.get("phase", "")).upper() == "TECHNICAL"
        and len(normalize_query(str(row.get("query_text", "")))) >= 15
    )
```

- [ ] **Step 3: Add read-only DB helper**

```python
from __future__ import annotations

import os

import pymysql


def connect():
    return pymysql.connect(
        host=os.environ["RETRIEVAL_EVAL_DB_HOST"],
        port=int(os.getenv("RETRIEVAL_EVAL_DB_PORT", "3306")),
        user=os.environ["RETRIEVAL_EVAL_DB_USER"],
        password=os.environ["RETRIEVAL_EVAL_DB_PASSWORD"],
        database=os.environ["RETRIEVAL_EVAL_DB_NAME"],
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )
```

- [ ] **Step 4: Add published atom exporter**

The exporter must execute:

```sql
SELECT atom_id, subject, category, difficulty, tags_json, principles, pitfalls, follow_up_paths_json, status
FROM knowledge_atom
WHERE status = 'PUBLISHED' AND category = %s
ORDER BY atom_id
```

Normalize `tags_json` and `follow_up_paths_json` into arrays, add `search_text` from `build_atom_text`, and write JSONL.

CLI:

```powershell
python scripts/retrieval_eval/export_atoms.py --output output/retrieval-eval/ai-model-atoms.jsonl
```

- [ ] **Step 5: Add real-query exporter**

The exporter must execute:

```sql
SELECT request_id, position, phase, query_text, candidate_count, retrieval_strategy, status, create_time
FROM rag_retrieval_request_log
WHERE position LIKE %s AND phase = 'TECHNICAL' AND status = 'SUCCESS'
ORDER BY create_time DESC
```

It must:

- anonymize `query_text`
- remove `request_id` before writing output
- filter invalid queries
- deduplicate normalized query text
- set `source` to `real_anonymized`
- set `scenario` to `null` because real-query scenario classification requires human review
- cap output with `--limit`, default `40`

CLI:

```powershell
python scripts/retrieval_eval/extract_real_queries.py --limit 40 --output output/retrieval-eval/real-queries.jsonl
```

- [ ] **Step 6: Run tests**

```powershell
python -m unittest tests.test_retrieval_eval_common -v
```

Expected: PASS.

- [ ] **Step 7: Commit exporters**

```bash
git add scripts/retrieval_eval/db.py scripts/retrieval_eval/export_atoms.py scripts/retrieval_eval/extract_real_queries.py scripts/retrieval_eval/common.py tests/test_retrieval_eval_common.py
git commit -m "feat: export retrieval evaluation source data"
```

## Task 3: Classify Real Queries And Generate Reviewed Synthetic Queries

**Files:**
- Create: `scripts/retrieval_eval/generate_synthetic_queries.py`
- Create: `tests/test_retrieval_eval_dataset.py`

- [ ] **Step 1: Review and classify exported real queries**

Open `output/retrieval-eval/real-queries.jsonl` and review every row before synthetic generation:

- confirm the query remains meaningful after anonymization
- remove invalid, duplicate, or privacy-sensitive rows
- set exactly one `scenario` value from the fixed scenario quota keys
- keep `source` as `real_anonymized`

The reviewed real-query file remains outside Git until it is merged into the final committed dataset.

- [ ] **Step 2: Write failing quota calculation tests**

```python
import unittest

from scripts.retrieval_eval.generate_synthetic_queries import remaining_quotas


class RetrievalEvalDatasetTest(unittest.TestCase):
    def test_remaining_quotas_subtract_real_query_scenarios(self):
        real_rows = [
            {"scenario": "correct_but_incomplete"},
            {"scenario": "correct_but_incomplete"},
            {"scenario": "concept_confusion"},
        ]
        quotas = remaining_quotas(real_rows)
        self.assertEqual(quotas["correct_but_incomplete"], 23)
        self.assertEqual(quotas["concept_confusion"], 19)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the test and verify it fails**

```powershell
python -m unittest tests.test_retrieval_eval_dataset -v
```

Expected: FAIL because the generator does not exist.

- [ ] **Step 4: Implement fixed scenario quotas**

```python
SCENARIO_QUOTAS = {
    "correct_but_incomplete": 25,
    "concept_confusion": 20,
    "easier_follow_up": 15,
    "mixed_topics": 15,
    "colloquial_or_typos": 10,
    "short_clear_topic": 10,
    "off_topic_noise": 5,
}


def remaining_quotas(real_rows):
    quotas = dict(SCENARIO_QUOTAS)
    for row in real_rows:
        scenario = row.get("scenario")
        if scenario in quotas and quotas[scenario] > 0:
            quotas[scenario] -= 1
    return quotas
```

- [ ] **Step 5: Implement OpenAI-compatible generation**

Use the same environment variables as `scripts/question_bank_import.py`:

```text
DEEPSEEK_BASE_URL
DEEPSEEK_API_KEY
DEEPSEEK_MODEL
```

The prompt must require:

- previous AI question
- candidate answer
- combined `query_text`
- one scenario value
- AI-model technical topic
- no personal data
- strict JSON array output

The script must merge real rows and generated rows, deduplicate them, and refuse to write unless the total is exactly 100.
It must also refuse any real row whose `scenario` is missing or is not one of the fixed quota keys.

CLI:

```powershell
python scripts/retrieval_eval/generate_synthetic_queries.py `
  --real-queries output/retrieval-eval/real-queries.jsonl `
  --atoms output/retrieval-eval/ai-model-atoms.jsonl `
  --output output/retrieval-eval/ai-model-v1-unjudged.jsonl
```

- [ ] **Step 6: Run tests**

```powershell
python -m unittest tests.test_retrieval_eval_dataset -v
```

Expected: PASS.

- [ ] **Step 7: Commit synthetic generation**

```bash
git add scripts/retrieval_eval/generate_synthetic_queries.py tests/test_retrieval_eval_dataset.py
git commit -m "feat: generate retrieval evaluation queries"
```

## Task 4: Score Embedding Models And Build Candidate Pools

**Files:**
- Create: `scripts/retrieval_eval/score_embeddings.py`
- Create: `scripts/retrieval_eval/build_candidate_pool.py`
- Create: `tests/test_retrieval_eval_pool.py`

- [ ] **Step 1: Write failing candidate pool tests**

```python
import unittest

from scripts.retrieval_eval.build_candidate_pool import merge_candidates


class RetrievalEvalPoolTest(unittest.TestCase):
    def test_merge_candidates_deduplicates_atoms_and_preserves_sources(self):
        rankings = {
            "all-minilm": [{"atom_id": "a", "rank": 1}, {"atom_id": "b", "rank": 2}],
            "bge-m3": [{"atom_id": "a", "rank": 3}, {"atom_id": "c", "rank": 1}],
        }
        merged = merge_candidates(rankings)
        by_id = {row["atom_id"]: row for row in merged}
        self.assertEqual(set(by_id), {"a", "b", "c"})
        self.assertEqual(set(by_id["a"]["sources"]), {"all-minilm", "bge-m3"})


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
python -m unittest tests.test_retrieval_eval_pool -v
```

Expected: FAIL because the pool builder does not exist.

- [ ] **Step 3: Implement model adapters**

`score_embeddings.py` must support:

```python
MODEL_CONFIGS = {
    "all-minilm": {
        "backend": "sentence_transformers",
        "model_name": "sentence-transformers/all-MiniLM-L6-v2",
        "query_prefix": "",
        "passage_prefix": "",
    },
    "multilingual-e5-base": {
        "backend": "sentence_transformers",
        "model_name": "intfloat/multilingual-e5-base",
        "query_prefix": "query: ",
        "passage_prefix": "passage: ",
    },
    "bge-m3": {
        "backend": "flag_embedding",
        "model_name": "BAAI/bge-m3",
        "query_prefix": "",
        "passage_prefix": "",
    },
}
```

For every query and model, write ranked Top-30 rows with:

```json
{
  "query_id": "ai-model-v1-0001",
  "model": "all-minilm",
  "results": [
    {"atom_id": "atom-a", "score": 0.82, "rank": 1}
  ]
}
```

CLI:

```powershell
python scripts/retrieval_eval/score_embeddings.py `
  --queries output/retrieval-eval/ai-model-v1-unjudged.jsonl `
  --atoms output/retrieval-eval/ai-model-atoms.jsonl `
  --output output/retrieval-eval/embedding-rankings.jsonl `
  --top-k 30
```

- [ ] **Step 4: Implement keyword retrieval and candidate merge**

The keyword baseline must tokenize lowercased query and atom search text using Chinese character bigrams plus whitespace-delimited Latin terms. Score by overlap count, then merge:

- each model Top-20
- keyword Top-20
- five deterministic random negatives outside all Top-20 lists

Use a fixed random seed. Store source ranks for reviewer context.

- [ ] **Step 5: Run tests**

```powershell
python -m unittest tests.test_retrieval_eval_pool -v
```

Expected: PASS.

- [ ] **Step 6: Commit ranking and pooling**

```bash
git add scripts/retrieval_eval/score_embeddings.py scripts/retrieval_eval/build_candidate_pool.py tests/test_retrieval_eval_pool.py
git commit -m "feat: build retrieval evaluation candidate pools"
```

## Task 5: Add Model Pre-Labeling And Human Review Output

**Files:**
- Create: `scripts/retrieval_eval/prelabel_candidates.py`
- Modify: `docs/superpowers/specs/2026-06-03-rag-retrieval-evaluation-design.md`

- [ ] **Step 1: Add the missing artifact to the design document**

Under `scripts/retrieval_eval/`, add:

```text
prelabel_candidates.py
```

- [ ] **Step 2: Implement pre-labeling**

The script must:

- read candidate pools and atom corpus
- call an OpenAI-compatible chat API
- require strict JSON output
- produce `relevance`, `reason`, and `confidence`
- never overwrite human-reviewed judgments
- clearly mark output as `model_suggestion`

The prompt must include the fixed rubric:

```text
3 = highly suitable direct next follow-up
2 = relevant and usable, but not best
1 = topically related, unsuitable for this follow-up
0 = irrelevant or causes topic jump
```

CLI:

```powershell
python scripts/retrieval_eval/prelabel_candidates.py `
  --queries output/retrieval-eval/ai-model-v1-unjudged.jsonl `
  --atoms output/retrieval-eval/ai-model-atoms.jsonl `
  --pool output/retrieval-eval/candidate-pool.jsonl `
  --output output/retrieval-eval/candidate-pool-prelabeled.jsonl
```

- [ ] **Step 3: Produce a human review file**

The output row must include:

```json
{
  "query_id": "ai-model-v1-0001",
  "atom_id": "atom-a",
  "source_ranks": {"all-minilm": 4, "bge-m3": 1},
  "model_suggestion": {"relevance": 3, "reason": "...", "confidence": 0.86},
  "human_judgment": null
}
```

Human reviewers fill `human_judgment` with the final relevance and reason.

- [ ] **Step 4: Commit pre-labeling**

```bash
git add scripts/retrieval_eval/prelabel_candidates.py docs/superpowers/specs/2026-06-03-rag-retrieval-evaluation-design.md
git commit -m "feat: add retrieval candidate prelabeling"
```

## Task 6: Calculate Metrics And Bootstrap Confidence Intervals

**Files:**
- Create: `scripts/retrieval_eval/calculate_metrics.py`
- Create: `tests/test_retrieval_eval_metrics.py`

- [ ] **Step 1: Write failing metric tests**

```python
import unittest

from scripts.retrieval_eval.calculate_metrics import hit_rate_at_k, recall_at_k


class RetrievalEvalMetricsTest(unittest.TestCase):
    def test_recall_at_k_uses_relevance_two_or_above(self):
        judgments = {"a": 3, "b": 2, "c": 1}
        ranking = ["a", "x", "c", "b"]
        self.assertEqual(recall_at_k(ranking, judgments, 3), 0.5)
        self.assertEqual(recall_at_k(ranking, judgments, 4), 1.0)

    def test_hit_rate_at_k_counts_queries_with_at_least_one_relevant_atom(self):
        rankings = {"q1": ["a"], "q2": ["x"]}
        judgments = {"q1": {"a": 2}, "q2": {"b": 3}}
        self.assertEqual(hit_rate_at_k(rankings, judgments, 1), 0.5)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests and verify they fail**

```powershell
python -m unittest tests.test_retrieval_eval_metrics -v
```

Expected: FAIL because metric functions do not exist.

- [ ] **Step 3: Implement metrics**

Implement:

```python
def recall_at_k(ranking, judgments, k, threshold=2):
    relevant = {atom_id for atom_id, score in judgments.items() if score >= threshold}
    if not relevant:
        return 0.0
    return len(relevant.intersection(ranking[:k])) / len(relevant)


def hit_rate_at_k(rankings, judgments_by_query, k, threshold=2):
    hits = 0
    for query_id, ranking in rankings.items():
        relevant = {
            atom_id
            for atom_id, score in judgments_by_query.get(query_id, {}).items()
            if score >= threshold
        }
        if relevant.intersection(ranking[:k]):
            hits += 1
    return hits / len(rankings) if rankings else 0.0
```

Also implement:

- NDCG@3 using graded relevance `0-3`
- MRR using relevance `3`, falling back to relevance `2` when no relevance `3` exists
- zero-hit rate
- per-query metric rows
- paired bootstrap 95% confidence intervals with a configurable seed
- Top-3, Top-10, Top-20, and Top-30 output

- [ ] **Step 4: Run tests**

```powershell
python -m unittest tests.test_retrieval_eval_metrics -v
```

Expected: PASS.

- [ ] **Step 5: Commit metrics**

```bash
git add scripts/retrieval_eval/calculate_metrics.py tests/test_retrieval_eval_metrics.py
git commit -m "feat: calculate retrieval evaluation metrics"
```

## Task 7: Validate And Commit The Fixed V1 Dataset And Atom Snapshot

**Files:**
- Create: `scripts/retrieval_eval/validate_dataset.py`
- Create: `backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl`
- Create: `backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl`
- Create: `backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json`
- Modify: `tests/test_retrieval_eval_dataset.py`

- [ ] **Step 1: Add failing dataset validation tests**

Add tests that assert:

- exactly 100 unique query IDs
- every atom snapshot row has a unique atom ID, category `AI大模型`, published source status, and matching `search_text`
- every judgment relevance is an integer `0-3`
- every judgment atom ID exists in the committed v1 atom snapshot
- no forbidden fields such as `user_id`, `record_id`, `request_id`, `email`, or `phone`
- source values are `real_anonymized` or `synthetic_reviewed`

- [ ] **Step 2: Implement dataset validator**

CLI:

```powershell
python scripts/retrieval_eval/validate_dataset.py `
  --dataset backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl `
  --metadata backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json `
  --atoms backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl
```

Expected behavior: exit `0` only when all validation rules pass; print actionable errors otherwise.

- [ ] **Step 3: Review and freeze the Atom snapshot**

Review `output/retrieval-eval/ai-model-atoms.jsonl`, then copy only the approved corpus into `backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl`.

The validator must reject the snapshot when:

- an atom ID is duplicated
- an atom is not from category `AI大模型`
- the source status is not `PUBLISHED`
- `search_text` does not equal `build_atom_text(atom)`
- forbidden identifiers, secrets, tokens, or internal URLs are present

Once committed, the v1 atom snapshot is immutable. Later question-bank changes require a new evaluation dataset version.

- [ ] **Step 4: Complete human review**

Use `candidate-pool-prelabeled.jsonl` as the review surface:

- review all suggested relevance `2` and `3`
- review ranking disagreements
- review low-confidence suggestions
- sample relevance `0` and `1`
- sample atoms outside pools

Write only reviewed judgments into `ai-model-v1.jsonl`. Set metadata fields:

```json
{
  "dataset_id": "retrieval-eval-ai-model-v1",
  "query_count": 100,
  "category": "AI大模型",
  "relevant_threshold": 2,
  "sources": {
    "real_anonymized": 0,
    "synthetic_reviewed": 100
  },
  "scenario_counts": {},
  "immutable": true
}
```

Replace source and scenario counts with the actual reviewed values.

- [ ] **Step 5: Run dataset validation**

```powershell
python -m unittest tests.test_retrieval_eval_dataset -v
python scripts/retrieval_eval/validate_dataset.py --dataset backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl --metadata backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json --atoms backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl
```

Expected: PASS and exit `0`.

- [ ] **Step 6: Commit the reviewed dataset**

```bash
git add scripts/retrieval_eval/validate_dataset.py tests/test_retrieval_eval_dataset.py backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json
git commit -m "test: add ai model retrieval evaluation dataset"
```

## Task 8: Produce The First Evaluation Report

**Files:**
- Generated, not committed by default: `output/retrieval-eval/ai-model-v1-metrics.json`
- Generated, not committed by default: `output/retrieval-eval/ai-model-v1-report.md`

- [ ] **Step 1: Install offline dependencies**

```powershell
python -m pip install -r scripts/retrieval_eval/requirements.txt
```

Expected: dependencies install successfully. This is an offline tooling environment, not a backend runtime dependency.

- [ ] **Step 2: Export atoms and real queries**

Set read-only DB environment variables, then run:

```powershell
python scripts/retrieval_eval/export_atoms.py --output output/retrieval-eval/ai-model-atoms.jsonl
python scripts/retrieval_eval/extract_real_queries.py --limit 40 --output output/retrieval-eval/real-queries.jsonl
```

Expected: no secrets printed; outputs contain only AI-model atoms and anonymized query rows.

- [ ] **Step 3: Score models and calculate metrics**

```powershell
python scripts/retrieval_eval/score_embeddings.py --queries backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl --atoms backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl --output output/retrieval-eval/embedding-rankings.jsonl --top-k 30
python scripts/retrieval_eval/calculate_metrics.py --dataset backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl --rankings output/retrieval-eval/embedding-rankings.jsonl --metrics-output output/retrieval-eval/ai-model-v1-metrics.json --report-output output/retrieval-eval/ai-model-v1-report.md --seed 20260603
```

Expected: metrics for all three models at Top-3, Top-10, Top-20, and Top-30, including paired bootstrap confidence intervals.

- [ ] **Step 4: Review decision examples**

The report must include:

- queries where Recall@20 succeeds but Recall@3 fails
- queries where all models fail
- queries where multilingual models outperform the baseline
- queries where the stronger model adds little value
- a recommendation: keep current embedding, migrate embedding, improve retrieval inputs, or proceed to reranking design

## Task 9: Document The Evaluation Workflow

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add README evaluation section**

Document:

- evaluation dataset location
- privacy rule
- optional dependency installation
- export, scoring, validation, and metric commands
- warning that offline evaluation does not modify production Qdrant

- [ ] **Step 2: Update CHANGELOG**

Under `## 未发布` -> `### 新增`, add:

```markdown
- 新增 AI 大模型岗位 RAG 离线检索评测工具链与固定评测集，用于比较候选集大小、中文/多语言 Embedding 模型和后续 rerank 价值。
```

- [ ] **Step 3: Run all Python tests**

```powershell
python -m unittest discover -s tests -v
```

Expected: PASS.

- [ ] **Step 4: Verify formatting**

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 5: Commit documentation**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: describe retrieval evaluation workflow"
```

## Task 10: Final Verification

**Files:**
- Verify all files changed by this plan.

- [ ] **Step 1: Run Python test suite**

```powershell
python -m unittest discover -s tests -v
```

Expected: PASS.

- [ ] **Step 2: Validate the committed dataset**

```powershell
python scripts/retrieval_eval/validate_dataset.py --dataset backend/src/test/resources/retrieval-eval/ai-model-v1.jsonl --metadata backend/src/test/resources/retrieval-eval/ai-model-v1-metadata.json --atoms backend/src/test/resources/retrieval-eval/ai-model-v1-atoms.jsonl
```

Expected: exit `0`.

- [ ] **Step 3: Verify worktree and commits**

```powershell
git diff --check
git status --short --branch
git log --oneline --decorate --max-count=12
```

Expected: clean worktree and frequent commits for scaffold, exports, generation, ranking, pre-labeling, metrics, dataset, and docs.
