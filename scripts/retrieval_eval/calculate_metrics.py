from __future__ import annotations

import argparse
import json
import math
import random
import statistics
import sys
from collections import Counter
from pathlib import Path
from typing import Any

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import RELEVANT_THRESHOLD, read_jsonl


K_VALUES = (3, 10, 20, 30)
BASELINE_MODEL = "all-minilm"
NEXT_ACTION_BY_SCORE = {
    3: "direct_follow_up",
    2: "bridged_follow_up",
    1: "clarify_or_narrow",
    0: "reset_or_redirect",
}
FOLLOW_UP_ACTIONS = {"direct_follow_up", "bridged_follow_up"}
NON_FOLLOW_UP_ACTIONS = {"clarify_or_narrow", "reset_or_redirect"}


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


def next_action_for_score(score: int) -> str:
    return NEXT_ACTION_BY_SCORE[max(0, min(3, score))]


def expected_next_action(query: dict[str, Any]) -> str:
    action = query.get("next_action")
    if isinstance(action, str) and action in set(NEXT_ACTION_BY_SCORE.values()):
        return action
    judgments = {str(judgment["atom_id"]): int(judgment["relevance"]) for judgment in query.get("judgments", [])}
    return next_action_for_score(max(judgments.values(), default=0))


def top_relevance(ranking: list[str], judgments: dict[str, int], k: int) -> int:
    return max((judgments.get(atom_id, 0) for atom_id in ranking[:k]), default=0)


def action_metrics_at_k(
    rankings: dict[str, list[str]],
    dataset: list[dict[str, Any]],
    judgments_by_query: dict[str, dict[str, int]],
    k: int,
) -> dict[str, Any]:
    if not dataset:
        return {}
    exact_matches = 0
    follow_up_expected = 0
    follow_up_supported = 0
    direct_expected = 0
    direct_supported = 0
    non_follow_up_expected = 0
    non_follow_up_safe = 0
    predicted_counts: Counter[str] = Counter()
    expected_counts: Counter[str] = Counter()
    mismatches: list[dict[str, Any]] = []

    for query in dataset:
        query_id = str(query["query_id"])
        expected = expected_next_action(query)
        top_score = top_relevance(rankings.get(query_id, []), judgments_by_query.get(query_id, {}), k)
        predicted = next_action_for_score(top_score)
        expected_counts[expected] += 1
        predicted_counts[predicted] += 1
        if predicted == expected:
            exact_matches += 1
        elif len(mismatches) < 20:
            mismatches.append(
                {
                    "query_id": query_id,
                    "expected": expected,
                    "predicted": predicted,
                    "top_relevance": top_score,
                }
            )
        if expected in FOLLOW_UP_ACTIONS:
            follow_up_expected += 1
            if top_score >= RELEVANT_THRESHOLD:
                follow_up_supported += 1
        if expected == "direct_follow_up":
            direct_expected += 1
            if top_score == 3:
                direct_supported += 1
        if expected in NON_FOLLOW_UP_ACTIONS:
            non_follow_up_expected += 1
            if predicted in NON_FOLLOW_UP_ACTIONS:
                non_follow_up_safe += 1

    return {
        "exact_action_rate": exact_matches / len(dataset),
        "follow_up_support_rate": follow_up_supported / follow_up_expected if follow_up_expected else 0.0,
        "direct_follow_up_support_rate": direct_supported / direct_expected if direct_expected else 0.0,
        "non_follow_up_safety_rate": non_follow_up_safe / non_follow_up_expected if non_follow_up_expected else 0.0,
        "expected_counts": dict(expected_counts),
        "predicted_counts": dict(predicted_counts),
        "mismatches": mismatches,
    }


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


def analyze_candidate_set(
    recall_at_3: list[float],
    recall_at_20: list[float],
    hit_at_3: list[float],
    hit_at_20: list[float],
    *,
    seed: int,
    bootstrap_samples: int = 5000,
) -> dict[str, Any]:
    recall_ci = paired_bootstrap_ci(
        recall_at_3,
        recall_at_20,
        seed=seed,
        samples=bootstrap_samples,
    )
    improved_fraction = (
        sum(candidate > baseline for baseline, candidate in zip(recall_at_3, recall_at_20))
        / len(recall_at_3)
        if recall_at_3
        else 0.0
    )
    hit_rate_delta = (
        statistics.fmean(hit_at_20) - statistics.fmean(hit_at_3)
        if hit_at_3
        else 0.0
    )
    conditions = {
        "mean_delta_recall_at_least_0_10": recall_ci["mean_delta"] >= 0.10,
        "improved_query_fraction_at_least_0_20": improved_fraction >= 0.20,
        "hit_rate_delta_at_least_0_05": hit_rate_delta >= 0.05,
        "bootstrap_lower_bound_above_zero": recall_ci["lower_95"] > 0.0,
    }
    return {
        "delta_recall_at_20_vs_3": recall_ci["mean_delta"],
        "improved_query_fraction": improved_fraction,
        "hit_rate_delta_at_20_vs_3": hit_rate_delta,
        "paired_bootstrap_95": recall_ci,
        "conditions": conditions,
        "meaningfully_better": all(conditions.values()),
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
    baseline_model: str = BASELINE_MODEL,
) -> dict[str, Any]:
    judgments = load_judgments(dataset)
    rankings_by_model = load_rankings(ranking_rows)
    query_ids = [str(row["query_id"]) for row in dataset]
    output: dict[str, Any] = {
        "query_count": len(query_ids),
        "models": {},
        "comparisons": {},
        "examples": {},
        "candidate_set_analysis": {},
        "next_action_analysis": {},
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
            metrics[f"hit_rate_at_{k}"] = statistics.fmean(
                1.0
                if relevant_atoms(judgments.get(query_id, {})).intersection(rankings.get(query_id, [])[:k])
                else 0.0
                for query_id in query_ids
            )
            metrics[f"zero_hit_rate_at_{k}"] = 1.0 - metrics[f"hit_rate_at_{k}"]
        metrics["ndcg_at_3"] = statistics.fmean(per_query[query_id]["ndcg_at_3"] for query_id in query_ids)
        metrics["mrr"] = statistics.fmean(per_query[query_id]["mrr"] for query_id in query_ids)
        output["models"][model] = {"metrics": metrics, "per_query": per_query}
        output["candidate_set_analysis"][model] = analyze_candidate_set(
            [per_query[query_id]["recall_at_3"] for query_id in query_ids],
            [per_query[query_id]["recall_at_20"] for query_id in query_ids],
            [
                1.0
                if relevant_atoms(judgments.get(query_id, {})).intersection(rankings.get(query_id, [])[:3])
                else 0.0
                for query_id in query_ids
            ],
            [
                1.0
                if relevant_atoms(judgments.get(query_id, {})).intersection(rankings.get(query_id, [])[:20])
                else 0.0
                for query_id in query_ids
            ],
            seed=seed,
        )
        output["examples"][f"{model}_recall_at_20_gt_recall_at_3"] = [
            query_id
            for query_id in query_ids
            if per_query[query_id]["recall_at_20"] > per_query[query_id]["recall_at_3"]
        ][:20]
        output["next_action_analysis"][model] = {
            f"top_{k}": action_metrics_at_k(rankings, dataset, judgments, k) for k in K_VALUES
        }

    if baseline_model in per_query_by_model:
        baseline = per_query_by_model[baseline_model]
        for model, per_query in per_query_by_model.items():
            if model == baseline_model:
                continue
            comparison: dict[str, Any] = {}
            for metric in ("recall_at_3", "recall_at_20", "ndcg_at_3", "mrr"):
                comparison[metric] = paired_bootstrap_ci(
                    [baseline[query_id][metric] for query_id in query_ids],
                    [per_query[query_id][metric] for query_id in query_ids],
                    seed=seed,
                )
            output["comparisons"][f"{model}_vs_{baseline_model}"] = comparison
            output["examples"][f"{model}_outperforms_{baseline_model}_at_3"] = [
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
    lines.extend(["## Candidate Set Analysis", ""])
    for model, analysis in metrics["candidate_set_analysis"].items():
        lines.append(
            f"- {model}: Recall@20 meaningfully better than Recall@3 = "
            f"{str(analysis['meaningfully_better']).lower()}; "
            f"deltaRecall={analysis['delta_recall_at_20_vs_3']:.4f}; "
            f"improvedQueries={analysis['improved_query_fraction']:.4f}; "
            f"deltaHitRate={analysis['hit_rate_delta_at_20_vs_3']:.4f}; "
            f"CI lower={analysis['paired_bootstrap_95']['lower_95']:.4f}"
        )
    lines.append("")
    lines.extend(["## Next Action Analysis", ""])
    lines.append("| Model | TopK | Exact Action | Follow-Up Support | Direct Support | Non-Follow-Up Safety |")
    lines.append("| --- | ---: | ---: | ---: | ---: | ---: |")
    for model, model_analysis in metrics.get("next_action_analysis", {}).items():
        for key, analysis in model_analysis.items():
            lines.append(
                f"| {model} | {key.removeprefix('top_')} | "
                f"{analysis['exact_action_rate']:.4f} | "
                f"{analysis['follow_up_support_rate']:.4f} | "
                f"{analysis['direct_follow_up_support_rate']:.4f} | "
                f"{analysis['non_follow_up_safety_rate']:.4f} |"
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
    parser.add_argument("--baseline-model", default=BASELINE_MODEL)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    metrics = calculate(
        read_jsonl(args.dataset),
        read_jsonl(args.rankings),
        seed=args.seed,
        baseline_model=args.baseline_model,
    )
    args.metrics_output.parent.mkdir(parents=True, exist_ok=True)
    args.metrics_output.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    args.report_output.parent.mkdir(parents=True, exist_ok=True)
    args.report_output.write_text(build_report(metrics), encoding="utf-8")
    print(f"wrote metrics: {args.metrics_output}")
    print(f"wrote report: {args.report_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
