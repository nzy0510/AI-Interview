from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Iterable


AI_MODEL_CATEGORY = "AI大模型"
RELEVANT_THRESHOLD = 2

EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
NAME_RE = re.compile(r"(?:我叫|我是|姓名是)\s*[\u4e00-\u9fff]{2,4}")
INTERNAL_URL_RE = re.compile(r"https?://(?:localhost|127\.0\.0\.1|[\w.-]+\.internal)(?::\d+)?\S*", re.I)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            row = json.loads(line)
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_number}: expected JSON object")
            rows.append(row)
    return rows


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def anonymize_text(value: str) -> str:
    text = EMAIL_RE.sub("[EMAIL]", value or "")
    text = PHONE_RE.sub("[PHONE]", text)
    text = NAME_RE.sub("[NAME]", text)
    text = INTERNAL_URL_RE.sub("[URL]", text)
    return text.strip()


def normalize_query(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip().lower()


def dedupe_queries(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    result: list[dict[str, Any]] = []
    for row in rows:
        key = normalize_query(str(row.get("query_text", "")))
        if key and key not in seen:
            seen.add(key)
            result.append(row)
    return result


def is_valid_real_query(row: dict[str, Any]) -> bool:
    return (
        AI_MODEL_CATEGORY in str(row.get("position", ""))
        and str(row.get("phase", "")).upper() == "TECHNICAL"
        and len(normalize_query(str(row.get("query_text", "")))) >= 15
    )


def build_atom_text(atom: dict[str, Any]) -> str:
    follow = atom.get("follow_up_paths", atom.get("follow_up_paths_json", []))
    if isinstance(follow, str):
        follow_text = follow
    else:
        follow_text = json.dumps(follow or [], ensure_ascii=False)
    return (
        f"考核点: {atom.get('subject', '')}\n"
        f"核心原理与标准答案: {atom.get('principles', '')}\n"
        f"面试常见陷阱与候选人易错点: {atom.get('pitfalls', '') or ''}\n"
        f"推荐的深度追问路径: {follow_text}"
    )
