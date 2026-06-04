import os
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "intfloat/multilingual-e5-base")
BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "32"))
NORMALIZE = os.getenv("EMBEDDING_NORMALIZE", "true").lower() == "true"

app = FastAPI()
model = SentenceTransformer(MODEL_NAME)


class EmbedRequest(BaseModel):
    texts: List[str] = Field(min_length=1)


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model": MODEL_NAME}


@app.post("/embed")
def embed(request: EmbedRequest) -> dict:
    vectors = model.encode(
        request.texts,
        batch_size=BATCH_SIZE,
        normalize_embeddings=NORMALIZE,
        show_progress_bar=False,
    )
    return {"embeddings": vectors.tolist()}
