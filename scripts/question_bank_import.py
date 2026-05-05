#!/usr/bin/env python3
"""
Build and optionally submit question-bank import packages.

Supported inputs:
  - PDF, DOCX, TXT, MD documents: parsed, chunked, and converted into atoms by
    an OpenAI-compatible chat API such as DeepSeek.
  - JSON atom/package files: normalized into the current import package schema.

Examples:
  python scripts/question_bank_import.py --input notes/java.pdf --category java --mode DRAFT
  python scripts/question_bank_import.py --input notes/redis --category redis --mode AUTO_PUBLISH --submit
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


def chunk_text(text: str, chunk_size: int, overlap: int) -> list[str]:
    chunks: list[str] = []
    cursor = 0
    step = max(1, chunk_size - overlap)
    while cursor < len(text):
        chunks.append(text[cursor : cursor + chunk_size])
        cursor += step
    return chunks


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


def call_chat_api(
    *,
    text: str,
    category: str,
    source_name: str,
    base_url: str,
    api_key: str,
    model: str,
    max_atoms: int,
) -> list[dict[str, Any]]:
    prompt = f"""
You are a senior technical interviewer and question-bank editor.
Convert the source text into up to {max_atoms} independent interview knowledge atoms.

Return only a valid JSON array. Do not wrap it in Markdown.

Each atom must use this schema:
[
  {{
    "id": "stable-english-slug-id",
    "subject": "knowledge point title",
    "category": "{category}",
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
    return parse_atoms(raw)


def parse_atoms(raw: str) -> list[dict[str, Any]]:
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
    return [normalize_atom(item) for item in parsed if isinstance(item, dict)]


def read_json_input(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict) and "atoms" in data:
        data = data["atoms"]
    if isinstance(data, dict):
        data = [data]
    if not isinstance(data, list):
        raise ValueError(f"Expected JSON atom, atom array, or import package: {path}")
    return [normalize_atom(item) for item in data if isinstance(item, dict)]


def normalize_atom(atom: dict[str, Any]) -> dict[str, Any]:
    content = atom.get("content") if isinstance(atom.get("content"), dict) else {}
    follow = content.get("followUpPaths", content.get("follow_up_paths", []))
    pitfalls = content.get("pitfalls", "")
    if isinstance(pitfalls, list):
        pitfalls = "\n".join(str(item) for item in pitfalls)
    if isinstance(follow, str):
        follow = [follow]
    normalized = {
        "id": str(atom.get("id", "")).strip(),
        "subject": str(atom.get("subject", "")).strip(),
        "category": str(atom.get("category", "")).strip(),
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


def slugify(value: str) -> str:
    value = value.lower().strip()
    value = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", value)
    value = re.sub(r"-+", "-", value).strip("-")
    return value[:80]


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


def validate_atoms(atoms: list[dict[str, Any]], default_category: str | None) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    seen: set[str] = set()
    for atom in atoms:
        atom_id = atom.get("id") or "<missing-id>"
        if atom.get("id") in seen:
            errors.append(f"{atom_id}: duplicate id")
        seen.add(atom.get("id", ""))
        if not atom.get("subject"):
            errors.append(f"{atom_id}: subject is required")
        if not atom.get("category") and not default_category:
            errors.append(f"{atom_id}: category is required")
        if not atom.get("content", {}).get("principles"):
            errors.append(f"{atom_id}: content.principles is required")
        if len(atom.get("content", {}).get("followUpPaths", [])) < 2:
            warnings.append(f"{atom_id}: at least two followUpPaths are recommended")
    return errors, warnings


def build_package(args: argparse.Namespace, atoms: list[dict[str, Any]], source_files: list[Path]) -> dict[str, Any]:
    for atom in atoms:
        if not atom.get("category") and args.category:
            atom["category"] = args.category
    atoms = dedupe_atoms(atoms)
    errors, warnings = validate_atoms(atoms, args.category)
    batch_id = args.batch_id or f"qb-{dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')}-{uuid.uuid4().hex[:6]}"
    return {
        "batchId": batch_id,
        "sourceRef": args.source_ref or ", ".join(str(path) for path in source_files),
        "targetCategory": args.category,
        "mode": args.mode.upper(),
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


def submit_package(package: dict[str, Any], api_url: str, token: str | None) -> dict[str, Any]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Question-Bank-Token"] = token
    request = urllib.request.Request(
        api_url,
        data=json.dumps(package, ensure_ascii=False).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"Import API failed: HTTP {exc.code} {body}") from exc


def generate_atoms(args: argparse.Namespace, files: list[Path]) -> list[dict[str, Any]]:
    atoms: list[dict[str, Any]] = []
    json_files = [path for path in files if path.suffix.lower() in JSON_EXTS]
    doc_files = [path for path in files if path.suffix.lower() in DOC_EXTS]

    for path in json_files:
        atoms.extend(read_json_input(path))

    if doc_files:
        if not args.category:
            raise RuntimeError("--category is required when converting documents")
        if not args.api_key:
            raise RuntimeError("DEEPSEEK_API_KEY or --api-key is required when converting documents")
        for path in doc_files:
            text = extract_text(path)
            if not text:
                print(f"skip empty document: {path}")
                continue
            chunks = chunk_text(text, args.chunk_size, args.overlap)
            for index, chunk in enumerate(chunks, start=1):
                print(f"generating atoms from {path.name} chunk {index}/{len(chunks)}")
                atoms.extend(
                    call_chat_api(
                        text=chunk,
                        category=args.category,
                        source_name=path.name,
                        base_url=args.base_url,
                        api_key=args.api_key,
                        model=args.model,
                        max_atoms=args.max_atoms_per_chunk,
                    )
                )
                time.sleep(args.pause_seconds)
    return atoms


def default_output_path(batch_id: str) -> Path:
    return Path("question_bank_imports") / f"{batch_id}.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build and submit question-bank import packages.")
    parser.add_argument("--input", action="append", required=True, help="Input file or directory. Repeatable.")
    parser.add_argument("--category", help="Default target category, for example java, mysql, redis, frontend.")
    parser.add_argument("--mode", default="DRAFT", choices=["DRY_RUN", "DRAFT", "AUTO_PUBLISH"])
    parser.add_argument("--batch-id")
    parser.add_argument("--source-ref")
    parser.add_argument("--output", help="Output package path. Defaults to question_bank_imports/<batchId>.json")
    parser.add_argument("--submit", action="store_true", help="Submit package to the backend import API.")
    parser.add_argument(
        "--api-url",
        default=os.getenv("QUESTION_BANK_IMPORT_URL", "http://localhost:8080/internal/question-bank/import"),
    )
    parser.add_argument("--token", default=os.getenv("QUESTION_BANK_ADMIN_TOKEN"))
    parser.add_argument("--base-url", default=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"))
    parser.add_argument("--api-key", default=os.getenv("DEEPSEEK_API_KEY"))
    parser.add_argument("--model", default=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"))
    parser.add_argument("--chunk-size", type=int, default=5000)
    parser.add_argument("--overlap", type=int, default=300)
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

    atoms = generate_atoms(args, files)
    package = build_package(args, atoms, files)
    output = Path(args.output).resolve() if args.output else default_output_path(package["batchId"]).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(package, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote import package: {output}")
    print(f"atoms: {len(package['atoms'])}")
    if package["validationReport"]["errors"]:
        print("validation errors:")
        for error in package["validationReport"]["errors"]:
            print(f"  - {error}")

    if args.submit and package["validationReport"]["errors"]:
        print("submit skipped because validation errors are present", file=sys.stderr)
        return 2

    if args.submit:
        response = submit_package(package, args.api_url, args.token)
        print(json.dumps(response, ensure_ascii=False, indent=2))
    return 0 if not package["validationReport"]["errors"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
