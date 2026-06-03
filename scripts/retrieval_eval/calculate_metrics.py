from __future__ import annotations

import argparse
import json
import math
import random
import statistics
from pathlib import Path
from typing import Any

from scripts.retrieval_eval.common import RELEVANT_THRESHOLD, read_jsonl


K_VALUES = (3, 10, 20, 30)
BASELINE_MODEL = "all-minilm"


def relevant_atoms(judgments: dict[str, int], threshold: int = RELEVANT_THRESHOLD) -> set[str]:
    return {atom_id for atom_id, score in judgments.items() if score >= threshold}


def recall_at_k(
    ranking: list[str],
    judgments: dict[str, int],
    k: int,
    threshold: int = RELEVANT_THRESHOLD,
) -> float:
    relevant = relevant_atoms(judgments, threshold)
    if not relevant:
        return 0.0
    return len(relevant.intersection(ranking[:k])) / len(relevant)


def hit_rate_at_k(
    rankings: dict[str, list[str]],
    judgments_by_query: dict[str, dict[str, int]],
    k: int,
    threshold: int = RELEVANT_THRESHOLD,
) -> float:
    if not rankings:
        return 0.0
    hits = sum(
        bool(relevant_atoms(judgments_by_query.get(query_id, {}), threshold).intersection(ranking[:k]))
        for query_id, ranking in rankings.items()
    )
    return hits / len(rankings)


def dcg(scores: list[int]) -> float:
    return sum((2**score - 1) / math.log2(index + 2) for index, score in enumerate(scores))


def ndcg_at_k(ranking: list[str], judgments: dict[str, int], k: int) -> float:
    actual = [judgments.get(atom_id, 0) for atom_id in ranking[:k]]
    ideal = sorted(judgments.values(), reverse=True)[:k]
    ideal_dcg = dcg(ideal)
    return dcg(actual) / ideal_dcg if ideal_dcg > 0 else 0.0


def mrr(ranking: list[str], judgments: dict[str, int]) -> float:
    target = 3 if any(score == 3 for score in judgments.values()) else 2
    for index, atom_id in enumerate(ranking, start=1):
        if judgments.get(atom_id, 0) >= target:
            return 1.0 / index
    return 0.0


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def paired_bootstrap_ci(
    baseline: list[float],
    candidate: list[float],
    *,
    seed: int,
    samples: int = 5000,
) -> dict[str, float]:
    if len(baseline) != len(candidate):
        raise ValueError("paired bootstrap inputs must have the same length")
    if not baseline:
        return {"mean_delta": 0.0, "lower_95": 0.0, "upper_95": 0.0}
    rng = random.Random(seed)
    deltas: list[float] = []
    for _ in range(samples):
        indices = [rng.randrange(len(baseline)) for _ in baseline]
        delta = statistics.fmean(candidate[index] - baseline[index] for index in indices)
        deltas.append(delta)
    return {
        "mean_delta": statistics.fmean(c - b for b, c in zip(baseline, candidate)),
        "lower_95": percentile(deltas, 0.025),
        "upper_95": percentile(deltas, 0.975),
    }


def load_judgments(dataset: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    return {
        str(query["query_id"]): {
            str(judgment["atom_id"]): int(judgment["relevance"])
            for judgment in query.get("judgments", [])
        }
        for query in dataset
    }


def load_rankings(rows: list[dict[str, Any]]) -> dict[str, dict[str, list[str]]]:
    rankings: dict[str, dict[str, list[str]]] = {}
    for row in rows:
        model = str(row["model"])
        query_id = str(row["query_id"])
        rankings.setdefault(model, {})[query_id] = [
            str(result["atom_id"]) for result in row.get("results", [])
        ]
    return rankings


def calculate(
    dataset: list[dict[str, Any]],
    ranking_rows: list[dict[str, Any]],
    *,
    seed: int,
) -> dict[str, Any]:
    judgments = load_judgments(dataset)
    rankings_by_model = load_rankings(ranking_rows)
    query_ids = [str(row["query_id"]) for row in dataset]
    output: dict[str, Any] = {
        "query_count": len(query_ids),
        "models": {},
        "comparisons": {},
        "examples": {},
    }
    per_query_by_model: dict[str, dict[str, dict[str, float]]] = {}

    for model, rankings in rankings_by_model.items():
        per_query: dict[str, dict[str, float]] = {}
        for query_id in query_ids:
            ranking = rankings.get(query_id, [])
            row = {f"recall_at_{k}": recall_at_k(ranking, judgments.get(query_id, {}), k) for k in K_VALUES}
            row["ndcg_at_3"] = ndcg_at_k(ranking, judgments.get(query_id, {}), 3)
            row["mrr"] = mrr(ranking, judgments.get(query_id, {}))
            per_query[query_id] = row
        per_query_by_model[model] = per_query
        metrics: dict[str, float] = {}
        for k in K_VALUES:
            metrics[f"recall_at_{k}"] = statistics.fmean(
                per_query[query_id][f"recall_at_{k}"] for query_id in query_ids
            )
            metrics[f"hit_rate_at_{k}"] = hit_rate_at_k(rankings, judgments, k)
            metrics[f"zero_hit_rate_at_{k}"] = 1.0 - metrics[f"hit_rate_at_{k}"]
        metrics["ndcg_at_3"] = statistics.fmean(per_query[query_id]["ndcg_at_3"] for query_id in query_ids)
        metrics["mrr"] = statistics.fmean(per_query[query_id]["mrr"] for query_id in query_ids)
        output["models"][model] = {"metrics": metrics, "per_query": per_query}
        output["examples"][f"{model}_recall_at_20_gt_recall_at_3"] = [
            query_id
            for query_id in query_ids
            if per_query[query_id]["recall_at_20"] > per_query[query_id]["recall_at_3"]
        ][:20]

    if BASELINE_MODEL in per_query_by_model:
        baseline = per_query_by_model[BASELINE_MODEL]
        for model, per_query in per_query_by_model.items():
            if model == BASELINE_MODEL:
                continue
            comparison: dict[str, Any] = {}
            for metric in ("recall_at_3", "recall_at_20", "ndcg_at_3", "mrr"):
                comparison[metric] = paired_bootstrap_ci(
                    [baseline[query_id][metric] for query_id in query_ids],
                    [per_query[query_id][metric] for query_id in query_ids],
                    seed=seed,
                )
            output["comparisons"][f"{model}_vs_{BASELINE_MODEL}"] = comparison
            output["examples"][f"{model}_outperforms_{BASELINE_MODEL}_at_3"] = [
                query_id
                for query_id in query_ids
                if per_query[query_id]["recall_at_3"] > baseline[query_id]["recall_at_3"]
            ][:20]
    output["examples"]["all_models_zero_hit_at_20"] = [
        query_id
        for query_id in query_ids
        if all(
            per_query[query_id]["recall_at_20"] == 0.0
            for per_query in per_query_by_model.values()
        )
    ][:20]
    return output


def build_report(metrics: dict[str, Any]) -> str:
    lines = ["# AI Model Retrieval Evaluation Report", "", f"Queries: {metrics['query_count']}", ""]
    lines.append("| Model | Recall@3 | Recall@20 | HitRate@3 | HitRate@20 | NDCG@3 | MRR |")
    lines.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
    for model, data in metrics["models"].items():
        row = data["metrics"]
        lines.append(
            f"| {model} | {row['recall_at_3']:.4f} | {row['recall_at_20']:.4f} | "
            f"{row['hit_rate_at_3']:.4f} | {row['hit_rate_at_20']:.4f} | "
            f"{row['ndcg_at_3']:.4f} | {row['mrr']:.4f} |"
        )
    lines.extend(["", "## Paired Bootstrap Comparisons", ""])
    for name, comparison in metrics["comparisons"].items():
        lines.append(f"### {name}")
        for metric, ci in comparison.items():
            lines.append(
                f"- {metric}: delta={ci['mean_delta']:.4f}, "
                f"95% CI=[{ci['lower_95']:.4f}, {ci['upper_95']:.4f}]"
            )
        lines.append("")
    lines.extend(["## Query-Level Examples", ""])
    for name, query_ids in metrics["examples"].items():
        lines.append(f"- {name}: {', '.join(query_ids) if query_ids else 'none'}")
    lines.extend(
        [
            "",
            "## Review Required",
            "",
            "- Decide whether the evidence supports embedding migration, retrieval-input changes, or reranking.",
            "",
        ]
    )
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Calculate retrieval evaluation metrics.")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--rankings", type=Path, required=True)
    parser.add_argument("--metrics-output", type=Path, required=True)
    parser.add_argument("--report-output", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=20260603)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    metrics = calculate(read_jsonl(args.dataset), read_jsonl(args.rankings), seed=args.seed)
    args.metrics_output.parent.mkdir(parents=True, exist_ok=True)
    args.metrics_output.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    args.report_output.parent.mkdir(parents=True, exist_ok=True)
    args.report_output.write_text(build_report(metrics), encoding="utf-8")
    print(f"wrote metrics: {args.metrics_output}")
    print(f"wrote report: {args.report_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
