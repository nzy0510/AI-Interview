from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Any

from scripts.retrieval_eval.common import dedupe_queries, read_jsonl, write_jsonl


SCENARIO_QUOTAS = {
    "correct_but_incomplete": 25,
    "concept_confusion": 20,
    "easier_follow_up": 15,
    "mixed_topics": 15,
    "colloquial_or_typos": 10,
    "short_clear_topic": 10,
    "off_topic_noise": 5,
}


def validate_real_query_scenarios(real_rows: list[dict[str, Any]]) -> None:
    for index, row in enumerate(real_rows, start=1):
        scenario = row.get("scenario")
        if scenario not in SCENARIO_QUOTAS:
            raise ValueError(
                f"real query {index} requires a reviewed scenario from: {', '.join(SCENARIO_QUOTAS)}"
            )


def remaining_quotas(real_rows: list[dict[str, Any]]) -> dict[str, int]:
    validate_real_query_scenarios(real_rows)
    counts = Counter(str(row["scenario"]) for row in real_rows)
    quotas: dict[str, int] = {}
    for scenario, target in SCENARIO_QUOTAS.items():
        if counts[scenario] > target:
            raise ValueError(f"real query scenario exceeds target quota: {scenario}")
        quotas[scenario] = target - counts[scenario]
    return quotas


def build_query_text(previous_ai_question: str, candidate_answer: str) -> str:
    question = previous_ai_question or ""
    if len(question) > 300:
        question = question[-300:]
    return f"{question} {candidate_answer or ''}".strip()


def compact_atom_context(atoms: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "atom_id": atom.get("atom_id"),
            "subject": atom.get("subject"),
            "tags": atom.get("tags", []),
            "principles": str(atom.get("principles", ""))[:500],
        }
        for atom in atoms
    ]


def parse_generated_queries(raw: str, scenario: str) -> list[dict[str, Any]]:
    cleaned = raw.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("\n", 1)[-1]
        cleaned = cleaned.rsplit("```", 1)[0].strip()
    parsed = json.loads(cleaned)
    if not isinstance(parsed, list):
        raise ValueError("expected a JSON array of generated queries")
    result: list[dict[str, Any]] = []
    for row in parsed:
        if not isinstance(row, dict):
            continue
        previous = str(row.get("previous_ai_question", "")).strip()
        answer = str(row.get("candidate_answer", "")).strip()
        if not previous or not answer:
            continue
        result.append(
            {
                "position": "AI大模型",
                "phase": "TECHNICAL",
                "source": "synthetic_unreviewed",
                "scenario": scenario,
                "previous_ai_question": previous,
                "candidate_answer": answer,
                "query_text": build_query_text(previous, answer),
            }
        )
    return result


def call_chat_api(
    *,
    scenario: str,
    count: int,
    atoms: list[dict[str, Any]],
    base_url: str,
    api_key: str,
    model: str,
) -> list[dict[str, Any]]:
    prompt = f"""
Generate exactly {count} distinct Chinese AI large-model technical interview retrieval queries.
Scenario: {scenario}

Each query must represent the production retrieval input: previous AI interviewer question plus
the candidate's current answer. Use realistic candidate language, no personal data, and no
company-confidential details. Return only a strict JSON array with:

[
  {{
    "previous_ai_question": "...",
    "candidate_answer": "..."
  }}
]

Use this published atom corpus only as topic coverage guidance:
{json.dumps(compact_atom_context(atoms), ensure_ascii=False)}
""".strip()
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "Return strict JSON only."},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.6,
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
    raw = data["choices"][0]["message"]["content"]
    return parse_generated_queries(raw, scenario)


def assign_query_ids(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, row in enumerate(rows, start=1):
        result.append({"query_id": f"ai-model-v1-{index:04d}", **row})
    return result


def generate_dataset(
    real_queries: list[dict[str, Any]],
    atoms: list[dict[str, Any]],
    *,
    base_url: str,
    api_key: str,
    model: str,
) -> list[dict[str, Any]]:
    quotas = remaining_quotas(real_queries)
    generated: list[dict[str, Any]] = []
    for scenario, count in quotas.items():
        if count == 0:
            continue
        rows = call_chat_api(
            scenario=scenario,
            count=count,
            atoms=atoms,
            base_url=base_url,
            api_key=api_key,
            model=model,
        )
        if len(rows) != count:
            raise ValueError(f"scenario {scenario}: expected {count} generated queries, got {len(rows)}")
        generated.extend(rows)
    merged = dedupe_queries([*real_queries, *generated])
    if len(merged) != 100:
        raise ValueError(f"expected exactly 100 unique queries, got {len(merged)}")
    return assign_query_ids(merged)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate synthetic queries to fill the AI-model v1 quota.")
    parser.add_argument("--real-queries", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"))
    parser.add_argument("--api-key", default=os.getenv("DEEPSEEK_API_KEY"))
    parser.add_argument("--model", default=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.api_key:
        raise RuntimeError("DEEPSEEK_API_KEY or --api-key is required")
    rows = generate_dataset(
        read_jsonl(args.real_queries),
        read_jsonl(args.atoms),
        base_url=args.base_url,
        api_key=args.api_key,
        model=args.model,
    )
    write_jsonl(args.output, rows)
    print(f"wrote {len(rows)} unreviewed queries: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
