from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import AI_MODEL_CATEGORY, build_atom_text, read_jsonl
from scripts.retrieval_eval.generate_synthetic_queries import SCENARIO_QUOTAS, build_query_text


ALLOWED_SOURCES = {"real_anonymized", "synthetic_reviewed"}
FORBIDDEN_FIELDS = {"user_id", "record_id", "request_id", "email", "phone"}
EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
SECRET_RE = re.compile(r"(?i)\b(api[_-]?key|token|password|secret)\s*[=:]\s*\S+")
INTERNAL_URL_RE = re.compile(r"https?://(?:localhost|127\.0\.0\.1|[\w.-]+\.internal)(?::\d+)?\S*", re.I)


def walk_values(value: Any) -> list[Any]:
    values = [value]
    if isinstance(value, dict):
        for child in value.values():
            values.extend(walk_values(child))
    elif isinstance(value, list):
        for child in value:
            values.extend(walk_values(child))
    return values


def find_forbidden_fields(value: Any, path: str = "$") -> list[str]:
    errors: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if str(key).lower() in FORBIDDEN_FIELDS:
                errors.append(f"{child_path}: forbidden field")
            errors.extend(find_forbidden_fields(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            errors.extend(find_forbidden_fields(child, f"{path}[{index}]"))
    return errors


def find_sensitive_text(value: Any, path: str) -> list[str]:
    errors: list[str] = []
    for item in walk_values(value):
        if not isinstance(item, str):
            continue
        if EMAIL_RE.search(item):
            errors.append(f"{path}: contains email")
        if PHONE_RE.search(item):
            errors.append(f"{path}: contains phone number")
        if SECRET_RE.search(item):
            errors.append(f"{path}: contains secret-like value")
        if INTERNAL_URL_RE.search(item):
            errors.append(f"{path}: contains internal URL")
    return errors


def validate_atom_snapshot(atoms: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for index, atom in enumerate(atoms, start=1):
        path = f"atoms[{index}]"
        atom_id = str(atom.get("atom_id", "")).strip()
        if not atom_id:
            errors.append(f"{path}: atom_id is required")
        elif atom_id in seen:
            errors.append(f"{path}: duplicate atom_id {atom_id}")
        seen.add(atom_id)
        if atom.get("category") != AI_MODEL_CATEGORY:
            errors.append(f"{path}: category must be {AI_MODEL_CATEGORY}")
        if atom.get("status") != "PUBLISHED":
            errors.append(f"{path}: status must be PUBLISHED")
        if atom.get("search_text") != build_atom_text(atom):
            errors.append(f"{path}: search_text does not match build_atom_text")
        errors.extend(find_forbidden_fields(atom, path))
        errors.extend(find_sensitive_text(atom, path))
    return errors


def validate_dataset_rows(
    rows: list[dict[str, Any]],
    atom_ids: set[str],
    *,
    expected_count: int = 100,
) -> list[str]:
    errors: list[str] = []
    query_ids: set[str] = set()
    for index, row in enumerate(rows, start=1):
        path = f"queries[{index}]"
        query_id = str(row.get("query_id", "")).strip()
        if not query_id:
            errors.append(f"{path}: query_id is required")
        elif query_id in query_ids:
            errors.append(f"{path}: duplicate query_id {query_id}")
        query_ids.add(query_id)
        if row.get("position") != AI_MODEL_CATEGORY:
            errors.append(f"{path}: position must be {AI_MODEL_CATEGORY}")
        if row.get("phase") != "TECHNICAL":
            errors.append(f"{path}: phase must be TECHNICAL")
        if row.get("source") not in ALLOWED_SOURCES:
            errors.append(f"{path}: source must be one of {sorted(ALLOWED_SOURCES)}")
        if row.get("scenario") not in SCENARIO_QUOTAS:
            errors.append(f"{path}: scenario must be one of {sorted(SCENARIO_QUOTAS)}")
        if not str(row.get("query_text", "")).strip():
            errors.append(f"{path}: query_text is required")
        previous = row.get("previous_ai_question")
        answer = row.get("candidate_answer")
        if previous is not None and answer is not None:
            if row.get("query_text") != build_query_text(str(previous), str(answer)):
                errors.append(f"{path}: query_text does not match production concatenation")
        judgments = row.get("judgments")
        if not isinstance(judgments, list) or not judgments:
            errors.append(f"{path}: judgments must be a non-empty array")
            judgments = []
        judged_atoms: set[str] = set()
        for judgment_index, judgment in enumerate(judgments, start=1):
            judgment_path = f"{path}.judgments[{judgment_index}]"
            atom_id = str(judgment.get("atom_id", ""))
            if atom_id in judged_atoms:
                errors.append(f"{judgment_path}: duplicate atom_id {atom_id}")
            judged_atoms.add(atom_id)
            if atom_id not in atom_ids:
                errors.append(f"{judgment_path}: unknown atom {atom_id}")
            relevance = judgment.get("relevance")
            if not isinstance(relevance, int) or isinstance(relevance, bool) or relevance not in {0, 1, 2, 3}:
                errors.append(f"{judgment_path}: relevance must be an integer from 0 to 3")
        errors.extend(find_forbidden_fields(row, path))
        errors.extend(find_sensitive_text(row, path))
    if len(rows) != expected_count:
        errors.append(f"dataset: expected {expected_count} queries, got {len(rows)}")
    return errors


def validate_metadata(metadata: dict[str, Any], rows: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    if metadata.get("dataset_id") != "retrieval-eval-ai-model-v1":
        errors.append("metadata: dataset_id must be retrieval-eval-ai-model-v1")
    if metadata.get("query_count") != len(rows):
        errors.append("metadata: query_count does not match dataset")
    if metadata.get("category") != AI_MODEL_CATEGORY:
        errors.append(f"metadata: category must be {AI_MODEL_CATEGORY}")
    if metadata.get("relevant_threshold") != 2:
        errors.append("metadata: relevant_threshold must be 2")
    if metadata.get("immutable") is not True:
        errors.append("metadata: immutable must be true")
    source_counts = dict(Counter(str(row.get("source")) for row in rows))
    scenario_counts = dict(Counter(str(row.get("scenario")) for row in rows))
    if metadata.get("sources") != source_counts:
        errors.append("metadata: sources distribution does not match dataset")
    if metadata.get("scenario_counts") != scenario_counts:
        errors.append("metadata: scenario_counts distribution does not match dataset")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate the committed AI-model retrieval evaluation dataset.")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    atoms = read_jsonl(args.atoms)
    rows = read_jsonl(args.dataset)
    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    errors = [
        *validate_atom_snapshot(atoms),
        *validate_dataset_rows(rows, {str(atom.get("atom_id", "")) for atom in atoms}),
        *validate_metadata(metadata, rows),
    ]
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f"validated {len(rows)} queries and {len(atoms)} atoms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
