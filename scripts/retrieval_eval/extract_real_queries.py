from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import anonymize_text, dedupe_queries, is_valid_real_query, write_jsonl
from scripts.retrieval_eval.db import connect


QUERY_SQL = """
SELECT request_id, position, phase, query_text, candidate_count, retrieval_strategy, status, create_time
FROM rag_retrieval_request_log
WHERE position LIKE %s AND phase = 'TECHNICAL' AND status = 'SUCCESS'
ORDER BY create_time DESC
""".strip()


def prepare_real_queries(rows: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    prepared: list[dict[str, Any]] = []
    for row in rows:
        query = {
            "position": row.get("position"),
            "phase": row.get("phase"),
            "source": "real_anonymized",
            "scenario": None,
            "query_text": anonymize_text(str(row.get("query_text") or "")),
        }
        if is_valid_real_query(query):
            prepared.append(query)
    return dedupe_queries(prepared)[: max(0, limit)]


def extract_real_queries(output: Path, limit: int) -> int:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(QUERY_SQL, ("%AI大模型%",))
            rows = cursor.fetchall()
    finally:
        connection.close()
    queries = prepare_real_queries(rows, limit)
    write_jsonl(output, queries)
    return len(queries)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export anonymized real AI-model retrieval queries.")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=40)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    count = extract_real_queries(args.output, args.limit)
    print(f"wrote {count} real queries: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
