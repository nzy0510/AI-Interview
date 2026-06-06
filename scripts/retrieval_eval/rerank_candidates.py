from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Callable

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import read_jsonl, write_jsonl


RERANKER_CONFIGS = {
    "bge-reranker-base": {
        "backend": "cross_encoder",
        "model_name": "BAAI/bge-reranker-base",
    },
    "bge-reranker-large": {
        "backend": "cross_encoder",
        "model_name": "BAAI/bge-reranker-large",
    },
    "bge-reranker-v2-m3": {
        "backend": "cross_encoder",
        "model_name": "BAAI/bge-reranker-v2-m3",
    },
}


def load_reranker(model_key: str, batch_size: int) -> Callable[[list[tuple[str, str]]], list[float]]:
    config = RERANKER_CONFIGS[model_key]
    if config["backend"] != "cross_encoder":
        raise ValueError(f"unsupported reranker backend: {config['backend']}")
    try:
        from sentence_transformers import CrossEncoder
    except ImportError as exc:
        raise RuntimeError("Install scripts/retrieval_eval/requirements.txt before reranking") from exc

    model = CrossEncoder(config["model_name"])

    def score_pairs(pairs: list[tuple[str, str]]) -> list[float]:
        scores = model.predict(pairs, batch_size=batch_size, show_progress_bar=True)
        return [float(score) for score in scores]

    return score_pairs


def rerank_rows(
    queries: list[dict[str, Any]],
    atoms: list[dict[str, Any]],
    ranking_rows: list[dict[str, Any]],
    *,
    source_model: str,
    output_model: str,
    candidate_top_k: int,
    score_pairs: Callable[[list[tuple[str, str]]], list[float]],
) -> list[dict[str, Any]]:
    query_by_id = {str(query["query_id"]): str(query.get("query_text", "")) for query in queries}
    atom_by_id = {str(atom["atom_id"]): str(atom.get("search_text", "")) for atom in atoms}
    output: list[dict[str, Any]] = []

    for row in ranking_rows:
        if str(row.get("model")) != source_model:
            continue
        query_id = str(row["query_id"])
        query_text = query_by_id.get(query_id, "")
        candidates = [
            result for result in row.get("results", [])[:candidate_top_k]
            if str(result.get("atom_id")) in atom_by_id
        ]
        pairs = [(query_text, atom_by_id[str(result["atom_id"])]) for result in candidates]
        rerank_scores = score_pairs(pairs) if pairs else []
        rescored = []
        for result, score in zip(candidates, rerank_scores):
            rescored.append(
                {
                    "atom_id": str(result["atom_id"]),
                    "score": float(score),
                    "source_score": float(result.get("score", 0.0)),
                    "source_rank": int(result.get("rank", len(rescored) + 1)),
                }
            )
        rescored.sort(key=lambda item: item["score"], reverse=True)
        for rank, result in enumerate(rescored, start=1):
            result["rank"] = rank
        output.append({"query_id": query_id, "model": output_model, "results": rescored})
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Rerank embedding candidates with a cross-encoder reranker.")
    parser.add_argument("--queries", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    parser.add_argument("--rankings", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-model", default="multilingual-e5-base")
    parser.add_argument("--candidate-top-k", type=int, default=20)
    parser.add_argument("--reranker", choices=RERANKER_CONFIGS, default="bge-reranker-base")
    parser.add_argument("--output-model")
    parser.add_argument("--batch-size", type=int, default=16)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_model = args.output_model or f"{args.source_model}+{args.reranker}"
    rows = rerank_rows(
        read_jsonl(args.queries),
        read_jsonl(args.atoms),
        read_jsonl(args.rankings),
        source_model=args.source_model,
        output_model=output_model,
        candidate_top_k=args.candidate_top_k,
        score_pairs=load_reranker(args.reranker, args.batch_size),
    )
    write_jsonl(args.output, rows)
    print(f"wrote {len(rows)} reranked rows: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
