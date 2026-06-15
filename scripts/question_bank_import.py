#!/usr/bin/env python3
"""
Build question-bank import packages for review in the InterWise knowledge-bank panel.

Supported inputs:
  - PDF, DOCX, TXT, MD documents: parsed, chunked, and converted into atoms by
    an OpenAI-compatible chat API such as DeepSeek.
  - JSON atom/package files: normalized into the current import package schema.

Examples:
  # Single category (all atoms share the same category)
  python scripts/question_bank_import.py --input notes/java.pdf --category java --mode DRAFT

  # Multi-category (LLM picks the best category per atom from the list)
  python scripts/question_bank_import.py --input notes/ops.md --categories linux,docker,kubernetes,shell,监控 --mode DRAFT
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Any

DOC_EXTS = {".pdf", ".docx", ".txt", ".md"}
JSON_EXTS = {".json"}
SUPPORTED_EXTS = DOC_EXTS | JSON_EXTS


# ── Text extraction ─────────────────────────────────────────────────────────────

def extract_text(path: Path) -> str:
    ext = path.suffix.lower()
    if ext in {".txt", ".md"}:
        return path.read_text(encoding="utf-8", errors="ignore").strip()
    if ext == ".pdf":
        try:
            import PyPDF2
        except ImportError as exc:
            raise RuntimeError("Missing dependency PyPDF2. Install with: pip install PyPDF2") from exc
        parts: list[str] = []
        with path.open("rb") as handle:
            reader = PyPDF2.PdfReader(handle)
            for page in reader.pages:
                text = page.extract_text()
                if text:
                    parts.append(text)
        return "\n".join(parts).strip()
    if ext == ".docx":
        try:
            from docx import Document
        except ImportError as exc:
            raise RuntimeError("Missing dependency python-docx. Install with: pip install python-docx") from exc
        doc = Document(str(path))
        return "\n".join(p.text for p in doc.paragraphs if p.text.strip()).strip()
    raise RuntimeError(f"Unsupported document type: {path}")


# ── Semantic chunking ───────────────────────────────────────────────────────────

def chunk_text(text: str, chunk_size: int = 5000, _overlap: int | None = None) -> list[str]:
    """Split text into chunks on paragraph boundaries.

    Unlike the old fixed-position sliding window, this version splits at blank-line
    boundaries so the LLM always receives complete paragraphs.  Very long paragraphs
    are further split on sentence boundaries.
    """
    # Split on blank lines (paragraph boundaries)
    raw_paragraphs = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    if not raw_paragraphs:
        return []

    chunks: list[str] = []
    buf: list[str] = []
    buf_len = 0

    for para in raw_paragraphs:
        para_len = len(para)

        # Very long single paragraph — split on sentence boundaries
        if para_len > chunk_size:
            if buf:
                chunks.append("\n\n".join(buf))
                buf = []
                buf_len = 0
            sub = _split_long_paragraph(para, chunk_size)
            chunks.extend(sub)
            continue

        # Would overflow — flush current chunk
        if buf_len + para_len > chunk_size and buf:
            chunks.append("\n\n".join(buf))
            buf = []
            buf_len = 0

        buf.append(para)
        buf_len += para_len

    if buf:
        chunks.append("\n\n".join(buf))

    return chunks


_SENTENCE_BOUNDARY = re.compile(r"(?<=[。.!?！？])\s*")


def _split_long_paragraph(text: str, max_len: int) -> list[str]:
    """Split a single over-long paragraph on sentence boundaries."""
    sentences = _SENTENCE_BOUNDARY.split(text)
    chunks: list[str] = []
    buf: list[str] = []
    buf_len = 0
    for sent in sentences:
        if not sent.strip():
            continue
        if buf_len + len(sent) > max_len and buf:
            chunks.append("".join(buf))
            buf = []
            buf_len = 0
        buf.append(sent)
        buf_len += len(sent)
    if buf:
        chunks.append("".join(buf))
    return chunks


# ── File collection ─────────────────────────────────────────────────────────────

def collect_files(paths: list[Path]) -> list[Path]:
    files: list[Path] = []
    for path in paths:
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTS:
            files.append(path)
        elif path.is_dir():
            for child in path.rglob("*"):
                if child.is_file() and child.suffix.lower() in SUPPORTED_EXTS:
                    files.append(child)
    return files


# ── LLM integration ─────────────────────────────────────────────────────────────

def _build_atom_schema_json(categories: list[str], source_name: str) -> str:
    """Build the category field for the LLM prompt.

    Single-category:  exact string (backward-compatible, zero ambiguity).
    Multi-category:   instruct the LLM to pick the best match per atom.
    """
    if len(categories) == 1:
        return f'"category": "{categories[0]}"'
    # Provide the valid list and ask the LLM to choose
    cat_list = ", ".join(categories)
    return f'"category": "从以下分类中选择最匹配的一个: [{cat_list}]"'


def call_chat_api(
    *,
    text: str,
    categories: list[str],
    source_name: str,
    base_url: str,
    api_key: str,
    model: str,
    max_atoms: int,
) -> list[dict[str, Any]]:
    category_field = _build_atom_schema_json(categories, source_name)
    prompt = f"""
You are a senior technical interviewer and question-bank editor.
Convert the source text into up to {max_atoms} independent interview knowledge atoms.

Return only a valid JSON array. Do not wrap it in Markdown.

Each atom must use this schema:
[
  {{
    "id": "stable-english-slug-id",
    "subject": "knowledge point title",
    {category_field},
    "difficulty": "junior|mid|senior|principal",
    "tags": ["tag1", "tag2"],
    "sourceRef": "{source_name}",
    "content": {{
      "principles": "core principles and expected answer",
      "pitfalls": "common mistakes or interviewer traps",
      "followUpPaths": [
        "follow-up question path for strong candidates",
        "guiding question path for weak candidates"
      ]
    }}
  }}
]

Skip page headers, directories, advertisements, or duplicated low-value content.
If no useful interview knowledge is present, return [].

Source text:
{text}
""".strip()
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "Return strict JSON only."},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.2,
    }
    endpoint = base_url.rstrip("/") + "/chat/completions"
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"Chat API failed: HTTP {exc.code} {body}") from exc
    raw = data["choices"][0]["message"]["content"]
    return parse_atoms(raw, categories)


# ── Atom parsing / normalization ────────────────────────────────────────────────

def parse_atoms(raw: str, valid_categories: list[str] | None = None) -> list[dict[str, Any]]:
    cleaned = raw.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    parsed = json.loads(cleaned)
    if isinstance(parsed, dict) and "atoms" in parsed:
        parsed = parsed["atoms"]
    if isinstance(parsed, dict):
        parsed = [parsed]
    if not isinstance(parsed, list):
        raise ValueError("Expected a JSON array of atoms")
    return [normalize_atom(item, valid_categories) for item in parsed if isinstance(item, dict)]


def read_json_input(path: Path, valid_categories: list[str] | None = None) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict) and "atoms" in data:
        data = data["atoms"]
    if isinstance(data, dict):
        data = [data]
    if not isinstance(data, list):
        raise ValueError(f"Expected JSON atom, atom array, or import package: {path}")
    return [normalize_atom(item, valid_categories) for item in data if isinstance(item, dict)]


def normalize_atom(atom: dict[str, Any], valid_categories: list[str] | None = None) -> dict[str, Any]:
    content = atom.get("content") if isinstance(atom.get("content"), dict) else {}
    follow = content.get("followUpPaths", content.get("follow_up_paths", []))
    pitfalls = content.get("pitfalls", "")
    if isinstance(pitfalls, list):
        pitfalls = "\n".join(str(item) for item in pitfalls)
    if isinstance(follow, str):
        follow = [follow]

    raw_category = str(atom.get("category", "")).strip()
    # If the LLM returned a fuzzy category, try to match it against the valid list
    category = _match_category(raw_category, valid_categories) if valid_categories else raw_category

    normalized = {
        "id": str(atom.get("id", "")).strip(),
        "subject": str(atom.get("subject", "")).strip(),
        "category": category,
        "difficulty": str(atom.get("difficulty", "mid")).strip() or "mid",
        "tags": [str(item).strip() for item in atom.get("tags", []) if str(item).strip()],
        "sourceRef": str(atom.get("sourceRef", atom.get("source_ref", ""))).strip(),
        "content": {
            "principles": str(content.get("principles", "")).strip(),
            "pitfalls": str(pitfalls).strip(),
            "followUpPaths": [str(item).strip() for item in follow if str(item).strip()],
        },
    }
    if not normalized["id"]:
        normalized["id"] = slugify(normalized["subject"]) or f"atom-{uuid.uuid4().hex[:10]}"
    return normalized


def _match_category(raw: str, valid: list[str]) -> str:
    """Map a fuzzy LLM-returned category to the nearest valid category name."""
    if not raw or not valid:
        return raw
    raw_lower = raw.strip().lower()
    # Exact match
    for v in valid:
        if v.lower() == raw_lower:
            return v
    # Substring match (e.g. LLM returned "Docker容器" -> matches "docker")
    for v in valid:
        if v.lower() in raw_lower or raw_lower in v.lower():
            return v
    # Return original raw — validation will flag it
    return raw


def slugify(value: str) -> str:
    value = value.lower().strip()
    value = re.sub(r"[^a-z0-9一-鿿]+", "-", value)
    value = re.sub(r"-+", "-", value).strip("-")
    return value[:80]


# ── Deduplication & validation ──────────────────────────────────────────────────

def dedupe_atoms(atoms: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    result: list[dict[str, Any]] = []
    for atom in atoms:
        atom_id = atom.get("id", "")
        base = atom_id
        suffix = 2
        while atom_id in seen:
            atom_id = f"{base}-{suffix}"
            suffix += 1
        atom["id"] = atom_id
        seen.add(atom_id)
        result.append(atom)
    return result


def validate_atoms(atoms: list[dict[str, Any]], categories: list[str]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    seen: set[str] = set()
    valid_lower = {c.lower() for c in categories} if categories else set()
    for atom in atoms:
        atom_id = atom.get("id") or "<missing-id>"
        if atom.get("id") in seen:
            errors.append(f"{atom_id}: duplicate id")
        seen.add(atom.get("id", ""))
        if not atom.get("subject"):
            errors.append(f"{atom_id}: subject is required")
        if not atom.get("category"):
            errors.append(f"{atom_id}: category is required")
        elif valid_lower and atom.get("category", "").strip().lower() not in valid_lower:
            warnings.append(
                f"{atom_id}: category '{atom.get('category')}' 不在候选列表 [{', '.join(categories)}] 中，请检查"
            )
        if not atom.get("content", {}).get("principles"):
            errors.append(f"{atom_id}: content.principles is required")
        if len(atom.get("content", {}).get("followUpPaths", [])) < 2:
            warnings.append(f"{atom_id}: at least two followUpPaths are recommended")
    return errors, warnings


# ── Package assembly ────────────────────────────────────────────────────────────

def build_package(categories: list[str], mode: str, atoms: list[dict[str, Any]],
                  source_files: list[Path], batch_id: str | None = None,
                  source_ref: str | None = None) -> dict[str, Any]:
    # Fill missing categories with the default (first in the list)
    default_cat = categories[0] if categories else ""
    for atom in atoms:
        if not atom.get("category") and default_cat:
            atom["category"] = default_cat
    atoms = dedupe_atoms(atoms)
    errors, warnings = validate_atoms(atoms, categories)
    bid = batch_id or f"qb-{dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')}-{uuid.uuid4().hex[:6]}"
    return {
        "batchId": bid,
        "sourceRef": source_ref or ", ".join(str(path) for path in source_files),
        "targetCategory": ", ".join(categories),
        "mode": mode.upper(),
        "atoms": atoms,
        "validationReport": {
            "tool": "scripts/question_bank_import.py",
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "errors": errors,
            "warnings": warnings,
        },
        "reviewReport": {
            "atomCount": len(atoms),
            "categories": sorted({atom.get("category", "") for atom in atoms if atom.get("category")}),
            "sourceFiles": [str(path) for path in source_files],
        },
    }


# ── Atom generation ─────────────────────────────────────────────────────────────

def generate_atoms(categories: list[str], files: list[Path],
                   base_url: str, api_key: str, model: str,
                   chunk_size: int, overlap: int,
                   max_atoms_per_chunk: int, pause_seconds: float) -> list[dict[str, Any]]:
    atoms: list[dict[str, Any]] = []
    json_files = [path for path in files if path.suffix.lower() in JSON_EXTS]
    doc_files = [path for path in files if path.suffix.lower() in DOC_EXTS]

    for path in json_files:
        atoms.extend(read_json_input(path, categories))

    if doc_files:
        if not api_key:
            raise RuntimeError("DEEPSEEK_API_KEY or --api-key is required when converting documents")
        for path in doc_files:
            text = extract_text(path)
            if not text:
                print(f"skip empty document: {path}")
                continue
            chunks = chunk_text(text, chunk_size)
            for index, chunk in enumerate(chunks, start=1):
                print(f"generating atoms from {path.name} chunk {index}/{len(chunks)}")
                atoms.extend(
                    call_chat_api(
                        text=chunk,
                        categories=categories,
                        source_name=path.name,
                        base_url=base_url,
                        api_key=api_key,
                        model=model,
                        max_atoms=max_atoms_per_chunk,
                    )
                )
                time.sleep(pause_seconds)
    return atoms


# ── CLI ─────────────────────────────────────────────────────────────────────────

def default_output_path(batch_id: str) -> Path:
    return Path("question_bank_imports") / f"{batch_id}.json"


def _resolve_categories(args: argparse.Namespace) -> list[str]:
    """Resolve the final categories list from --category and/or --categories."""
    cats: list[str] = []
    if args.categories:
        cats = [c.strip() for c in args.categories.split(",") if c.strip()]
    if args.category:
        c = args.category.strip()
        if c not in cats:
            cats.append(c)
    return cats


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build question-bank import packages for knowledge-bank review.")
    parser.add_argument("--input", action="append", required=True,
                        help="Input file or directory. Repeatable.")
    parser.add_argument("--category",
                        help="Default category (single). Use --categories for multi-category documents.")
    parser.add_argument("--categories",
                        help="Comma-separated list of valid categories. LLM picks the best per atom.")
    parser.add_argument("--mode", default="DRAFT", choices=["DRY_RUN", "DRAFT", "AUTO_PUBLISH"])
    parser.add_argument("--batch-id")
    parser.add_argument("--source-ref")
    parser.add_argument("--output",
                        help="Output package path. Defaults to question_bank_imports/<batchId>.json")
    parser.add_argument("--base-url", default=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"))
    parser.add_argument("--api-key", default=os.getenv("DEEPSEEK_API_KEY"))
    parser.add_argument("--model", default=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))
    parser.add_argument("--chunk-size", type=int, default=5000,
                        help="Soft max characters per chunk (cuts at paragraph boundaries).")
    parser.add_argument("--overlap", type=int, default=0,
                        help="(deprecated) Not needed with paragraph-boundary chunking.")
    parser.add_argument("--max-atoms-per-chunk", type=int, default=8)
    parser.add_argument("--pause-seconds", type=float, default=0.2)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    input_paths = [Path(item).expanduser().resolve() for item in args.input]
    files = collect_files(input_paths)
    if not files:
        print("No supported input files found.", file=sys.stderr)
        return 1

    categories = _resolve_categories(args)
    if not categories:
        print("error: --category or --categories is required when converting documents", file=sys.stderr)
        return 1

    print(f"categories: {categories}")

    atoms = generate_atoms(
        categories=categories,
        files=files,
        base_url=args.base_url,
        api_key=args.api_key,
        model=args.model,
        chunk_size=args.chunk_size,
        overlap=args.overlap,
        max_atoms_per_chunk=args.max_atoms_per_chunk,
        pause_seconds=args.pause_seconds,
    )

    package = build_package(
        categories=categories,
        mode=args.mode,
        atoms=atoms,
        source_files=files,
        batch_id=args.batch_id,
        source_ref=args.source_ref,
    )
    output = Path(args.output).resolve() if args.output else default_output_path(package["batchId"]).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(package, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote import package: {output}")
    print(f"atoms: {len(package['atoms'])}")
    print(f"categories found: {package['reviewReport']['categories']}")
    if package["validationReport"]["errors"]:
        print("validation errors:")
        for error in package["validationReport"]["errors"]:
            print(f"  - {error}")
    if package["validationReport"]["warnings"]:
        print("validation warnings:")
        for warning in package["validationReport"]["warnings"]:
            print(f"  - {warning}")

    return 0 if not package["validationReport"]["errors"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
