from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

if __package__ is None or __package__ == "":
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.retrieval_eval.common import read_jsonl, write_jsonl


MODEL_CONFIGS = {
    "all-minilm": {
        "backend": "sentence_transformers",
        "model_name": "sentence-transformers/all-MiniLM-L6-v2",
        "query_prefix": "",
        "passage_prefix": "",
    },
    "multilingual-e5-base": {
        "backend": "sentence_transformers",
        "model_name": "intfloat/multilingual-e5-base",
        "query_prefix": "query: ",
        "passage_prefix": "passage: ",
    },
    "bge-m3": {
        "backend": "flag_embedding",
        "model_name": "BAAI/bge-m3",
        "query_prefix": "",
        "passage_prefix": "",
    },
}


def load_encoder(config: dict[str, str]) -> Any:
    if config["backend"] == "sentence_transformers":
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as exc:
            raise RuntimeError("Install scripts/retrieval_eval/requirements.txt before scoring") from exc
        model = SentenceTransformer(config["model_name"])
        return lambda texts: model.encode(texts, normalize_embeddings=True, show_progress_bar=True)
    if config["backend"] == "flag_embedding":
        try:
            from FlagEmbedding import BGEM3FlagModel
        except ImportError as exc:
            raise RuntimeError("Install scripts/retrieval_eval/requirements.txt before scoring") from exc
        model = BGEM3FlagModel(config["model_name"], use_fp16=False)
        return lambda texts: model.encode(texts, batch_size=12, max_length=8192)["dense_vecs"]
    raise ValueError(f"unsupported backend: {config['backend']}")


def rank_model(
    model_key: str,
    queries: list[dict[str, Any]],
    atoms: list[dict[str, Any]],
    top_k: int,
) -> list[dict[str, Any]]:
    try:
        import numpy as np
    except ImportError as exc:
        raise RuntimeError("Install scripts/retrieval_eval/requirements.txt before scoring") from exc

    config = MODEL_CONFIGS[model_key]
    query_texts = [config["query_prefix"] + str(row.get("query_text", "")) for row in queries]
    atom_texts = [config["passage_prefix"] + str(row.get("search_text", "")) for row in atoms]
    encode = load_encoder(config)
    query_vectors = np.asarray(encode(query_texts), dtype=float)
    atom_vectors = np.asarray(encode(atom_texts), dtype=float)
    query_vectors /= np.maximum(np.linalg.norm(query_vectors, axis=1, keepdims=True), 1e-12)
    atom_vectors /= np.maximum(np.linalg.norm(atom_vectors, axis=1, keepdims=True), 1e-12)
    scores = query_vectors @ atom_vectors.T

    rows: list[dict[str, Any]] = []
    for query_index, query in enumerate(queries):
        ranked_indices = np.argsort(-scores[query_index])[:top_k]
        results = [
            {
                "atom_id": atoms[int(atom_index)]["atom_id"],
                "score": float(scores[query_index, int(atom_index)]),
                "rank": rank,
            }
            for rank, atom_index in enumerate(ranked_indices, start=1)
        ]
        rows.append({"query_id": query["query_id"], "model": model_key, "results": results})
    return rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Score retrieval queries with embedding models.")
    parser.add_argument("--queries", type=Path, required=True)
    parser.add_argument("--atoms", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--top-k", type=int, default=30)
    parser.add_argument("--models", nargs="+", choices=MODEL_CONFIGS, default=list(MODEL_CONFIGS))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    queries = read_jsonl(args.queries)
    atoms = read_jsonl(args.atoms)
    rows: list[dict[str, Any]] = []
    for model_key in args.models:
        print(f"scoring model: {model_key}")
        rows.extend(rank_model(model_key, queries, atoms, args.top_k))
    write_jsonl(args.output, rows)
    print(f"wrote {len(rows)} ranking rows: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
