from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from scripts.retrieval_eval.common import AI_MODEL_CATEGORY, build_atom_text, write_jsonl
from scripts.retrieval_eval.db import connect


ATOM_SQL = """
SELECT atom_id, subject, category, difficulty, tags_json, principles, pitfalls,
       follow_up_paths_json, status
FROM knowledge_atom
WHERE status = 'PUBLISHED' AND category = %s
ORDER BY atom_id
""".strip()


def parse_json_array(value: Any) -> list[Any]:
    if value is None or value == "":
        return []
    if isinstance(value, list):
        return value
    parsed = json.loads(str(value))
    return parsed if isinstance(parsed, list) else []


def normalize_atom_row(row: dict[str, Any]) -> dict[str, Any]:
    atom = {
        "atom_id": str(row.get("atom_id", "")),
        "subject": str(row.get("subject", "")),
        "category": str(row.get("category", "")),
        "difficulty": row.get("difficulty"),
        "tags": parse_json_array(row.get("tags_json")),
        "principles": str(row.get("principles", "")),
        "pitfalls": str(row.get("pitfalls") or ""),
        "follow_up_paths": parse_json_array(row.get("follow_up_paths_json")),
        "status": str(row.get("status", "")),
    }
    atom["search_text"] = build_atom_text(atom)
    return atom


def export_atoms(output: Path) -> int:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(ATOM_SQL, (AI_MODEL_CATEGORY,))
            rows = cursor.fetchall()
    finally:
        connection.close()
    atoms = [normalize_atom_row(row) for row in rows]
    write_jsonl(output, atoms)
    return len(atoms)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export published AI-model atoms for retrieval evaluation.")
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    count = export_atoms(args.output)
    print(f"wrote {count} atoms: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
