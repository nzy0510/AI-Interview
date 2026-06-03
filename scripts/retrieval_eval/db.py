from __future__ import annotations

import os
from typing import Any


def connect() -> Any:
    try:
        import pymysql
    except ImportError as exc:
        raise RuntimeError(
            "Missing PyMySQL. Install with: python -m pip install -r scripts/retrieval_eval/requirements.txt"
        ) from exc

    connection = pymysql.connect(
        host=os.environ["RETRIEVAL_EVAL_DB_HOST"],
        port=int(os.getenv("RETRIEVAL_EVAL_DB_PORT", "3306")),
        user=os.environ["RETRIEVAL_EVAL_DB_USER"],
        password=os.environ["RETRIEVAL_EVAL_DB_PASSWORD"],
        database=os.environ["RETRIEVAL_EVAL_DB_NAME"],
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )
    with connection.cursor() as cursor:
        cursor.execute("SET SESSION TRANSACTION READ ONLY")
    return connection
