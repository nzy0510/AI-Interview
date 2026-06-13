from pathlib import Path
import tempfile

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from markitdown import MarkItDown


MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024
SUPPORTED_EXTENSIONS = {"pdf", "docx", "md", "markdown", "txt"}

app = FastAPI(title="InterWise Document Converter")
converter = MarkItDown()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/convert")
async def convert(file: UploadFile = File(...), filename: str | None = Form(default=None)) -> dict[str, str]:
    safe_name = _safe_filename(filename or file.filename or "document")
    extension = _extension(safe_name)
    if extension not in SUPPORTED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="unsupported file type")

    content = await file.read(MAX_FILE_SIZE_BYTES + 1)
    if len(content) > MAX_FILE_SIZE_BYTES:
        raise HTTPException(status_code=400, detail="file exceeds 20MB")

    if extension in {"md", "markdown", "txt"}:
        return {"markdown": _decode_text(content)}

    with tempfile.TemporaryDirectory(prefix="interwise-convert-") as tmp_dir:
        path = Path(tmp_dir) / safe_name
        path.write_bytes(content)
        result = converter.convert(str(path))
        return {"markdown": result.text_content or ""}


def _extension(filename: str) -> str:
    suffix = Path(filename).suffix.lower()
    return suffix[1:] if suffix.startswith(".") else ""


def _safe_filename(filename: str) -> str:
    name = filename.replace("\\", "/").rsplit("/", 1)[-1].strip()
    if not name or name in {".", ".."}:
        return "document"
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in name)[-120:]


def _decode_text(content: bytes) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gb18030"):
        try:
            return content.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise HTTPException(status_code=400, detail="unsupported text encoding")
