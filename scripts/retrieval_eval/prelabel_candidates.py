from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import read_jsonl, write_jsonl


RUBRIC = """
3 = highly suitable direct next follow-up
2 = relevant and usable, but not best
1 = topically related, unsuitable for this follow-up
0 = irrelevant or causes topic jump
""".strip()


def parse_suggestions(raw: str) -> dict[str, dict[str, Any]]:
    cleaned = raw.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("\n", 1)[-1]
        cleaned = cleaned.rsplit("```", 1)[0].strip()
    parsed = json.loads(cleaned)
    if not isinstance(parsed, list):
        raise ValueError("expected a JSON array of relevance suggestions")
    suggestions: dict[str, dict[str, Any]] = {}
    for row in parsed:
        if not isinstance(row, dict) or not row.get("atom_id"):
            continue
        relevance = int(row.get("relevance", -1))
        if relevance not in {0, 1, 2, 3}:
            raise ValueError(f"invalid relevance for atom {row.get('atom_id')}: {relevance}")
        confidence = float(row.get("confidence", 0.0))
        suggestions[str(row["atom_id"])] = {
            "relevance": relevance,
            "reason": str(row.get("reason", "")).strip(),
            "confidence": max(0.0, min(1.0, confidence)),
        }
    return suggestions


def merge_suggestions(
    query_id: str,
    candidates: list[dict[str, Any]],
    suggestions: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for candidate in candidates:
        atom_id = str(candidate["atom_id"])
        rows.append(
            {
                "query_id": query_id,
                "atom_id": atom_id,
                "sources": candidate.get("sources", []),
                "source_ranks": candidate.get("source_ranks", {}),
                "source_scores": candidate.get("source_scores", {}),
                "model_suggestion": suggestions.get(atom_id),
                "human_judgment": candidate.get("human_judgment"),
            }
        )
    return rows


def call_chat_api(
    *,
    query: dict[str, Any],
    candidates: list[dict[str, Any]],
    atoms_by_id: dict[str, dict[str, Any]],
    base_url: str,
    api_key: str,
    model: str,
) -> dict[str, dict[str, Any]]:
    atom_rows = [
        {
            "atom_id": candidate["atom_id"],
            "subject": atoms_by_id.get(str(candidate["atom_id"]), {}).get("subject"),
            "search_text": atoms_by_id.get(str(candidate["atom_id"]), {}).get("search_text"),
        }
        for candidate in candidates
        if candidate.get("human_judgment") is None
    ]
    if not atom_rows:
        return {}
    prompt = f"""
Judge whether each knowledge atom is suitable as the basis for the next interviewer follow-up
after the candidate answer. This is contextual follow-up relevance, not text similarity.

Rubric:
{RUBRIC}

Return only a strict JSON array:
[
  {{
    "atom_id": "...",
    "relevance": 0,
    "reason": "one concise sentence",
    "confidence": 0.0
  }}
]

Query:
{json.dumps(query, ensure_ascii=False)}

Candidate atoms:
{json.dumps(atom_rows, ensure_ascii=False)}
""".strip()
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "Return strict JSON only."},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.1,
    }
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"Chat API failed: HTTP {exc.code} {body}") from exc
    return parse_suggestions(data["choices"][0]["message"]["content"])


def prelabel(
    queries: list[dict[str, Any]],
    atoms: list[dict[str, Any]],
    pools: list[dict[str, Any]],
    *,
    base_url: str,
    api_key: str,
    model: str,
) -> list[dict[str, Any]]:
    queries_by_id = {str(row["query_id"]): row for row in queries}
    atoms_by_id = {str(row["atom_id"]): row for row in atoms}
    output: list[dict[str, Any]] = []
    for pool in pools:
        query_id = str(pool["query_id"])
        candidates = list(pool.get("candidates", []))
        suggestions = call_chat_api(
            query=queries_by_id[query_id],
            candidates=candidates,
            atoms_by_id=atoms_by_id,
            base_url=base_url,
            api_key=api_key,
            model=model,
        )
        output.extend(merge_suggestions(query_id, candidates, suggestions))
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Pre-label pooled query-atom relevance for human review.")
    parser.add_argument("--queries", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    parser.add_argument("--pool", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"))
    parser.add_argument("--api-key", default=os.getenv("DEEPSEEK_API_KEY"))
    parser.add_argument("--model", default=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.api_key:
        raise RuntimeError("DEEPSEEK_API_KEY or --api-key is required")
    rows = prelabel(
        read_jsonl(args.queries),
        read_jsonl(args.atoms),
        read_jsonl(args.pool),
        base_url=args.base_url,
        api_key=args.api_key,
        model=args.model,
    )
    write_jsonl(args.output, rows)
    print(f"wrote {len(rows)} model suggestions for human review: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
