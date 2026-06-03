from __future__ import annotations

import argparse
import random
import re
from pathlib import Path
from typing import Any

from scripts.retrieval_eval.common import read_jsonl, write_jsonl


LATIN_TERM_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]*")
CHINESE_RUN_RE = re.compile(r"[\u4e00-\u9fff]+")


def tokenize(text: str) -> set[str]:
    lowered = (text or "").lower()
    tokens = {match.group(0) for match in LATIN_TERM_RE.finditer(lowered)}
    for match in CHINESE_RUN_RE.finditer(lowered):
        run = match.group(0)
        if len(run) == 1:
            tokens.add(run)
        else:
            tokens.update(run[index : index + 2] for index in range(len(run) - 1))
    return tokens


def keyword_ranking(query_text: str, atoms: list[dict[str, Any]], top_k: int) -> list[dict[str, Any]]:
    query_tokens = tokenize(query_text)
    scored: list[tuple[int, str]] = []
    for atom in atoms:
        overlap = len(query_tokens.intersection(tokenize(str(atom.get("search_text", "")))))
        scored.append((overlap, str(atom["atom_id"])))
    scored.sort(key=lambda item: (-item[0], item[1]))
    return [
        {"atom_id": atom_id, "score": float(score), "rank": rank}
        for rank, (score, atom_id) in enumerate(scored[:top_k], start=1)
    ]


def merge_candidates(rankings: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    merged: dict[str, dict[str, Any]] = {}
    for source, rows in rankings.items():
        for row in rows:
            atom_id = str(row["atom_id"])
            item = merged.setdefault(
                atom_id,
                {"atom_id": atom_id, "sources": [], "source_ranks": {}, "source_scores": {}},
            )
            item["sources"].append(source)
            item["source_ranks"][source] = int(row["rank"])
            item["source_scores"][source] = float(row.get("score", 0.0))
    return sorted(
        merged.values(),
        key=lambda item: (min(item["source_ranks"].values()), item["atom_id"]),
    )


def group_rankings(rows: list[dict[str, Any]], top_k: int) -> dict[str, dict[str, list[dict[str, Any]]]]:
    grouped: dict[str, dict[str, list[dict[str, Any]]]] = {}
    for row in rows:
        grouped.setdefault(str(row["query_id"]), {})[str(row["model"])] = list(row["results"])[:top_k]
    return grouped


def build_pools(
    queries: list[dict[str, Any]],
    atoms: list[dict[str, Any]],
    ranking_rows: list[dict[str, Any]],
    *,
    top_k: int = 20,
    random_negatives: int = 5,
    seed: int = 20260603,
) -> list[dict[str, Any]]:
    grouped = group_rankings(ranking_rows, top_k)
    all_atom_ids = [str(atom["atom_id"]) for atom in atoms]
    output: list[dict[str, Any]] = []
    for query in queries:
        query_id = str(query["query_id"])
        sources = dict(grouped.get(query_id, {}))
        sources["keyword"] = keyword_ranking(str(query.get("query_text", "")), atoms, top_k)
        pooled_ids = {
            str(row["atom_id"])
            for rows in sources.values()
            for row in rows
        }
        outside = [atom_id for atom_id in all_atom_ids if atom_id not in pooled_ids]
        rng = random.Random(f"{seed}:{query_id}")
        sample = rng.sample(outside, min(random_negatives, len(outside)))
        sources["random_negative"] = [
            {"atom_id": atom_id, "score": 0.0, "rank": rank}
            for rank, atom_id in enumerate(sample, start=1)
        ]
        output.append({"query_id": query_id, "candidates": merge_candidates(sources)})
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build pooled retrieval candidates for human review.")
    parser.add_argument("--queries", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    parser.add_argument("--rankings", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--top-k", type=int, default=20)
    parser.add_argument("--random-negatives", type=int, default=5)
    parser.add_argument("--seed", type=int, default=20260603)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    pools = build_pools(
        read_jsonl(args.queries),
        read_jsonl(args.atoms),
        read_jsonl(args.rankings),
        top_k=args.top_k,
        random_negatives=args.random_negatives,
        seed=args.seed,
    )
    write_jsonl(args.output, pools)
    print(f"wrote {len(pools)} candidate pools: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
