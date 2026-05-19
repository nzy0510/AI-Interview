from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
MCP_SKILL_ROOT = REPO_ROOT / "services" / "mcp-skill"
sys.path.insert(0, str(MCP_SKILL_ROOT))

from mcp_server.config import Settings  # noqa: E402
from mcp_server.question_bank import QuestionBankService  # noqa: E402


CONTRACT_DIR = REPO_ROOT / "question_bank_imports" / "fixtures" / "import-lifecycle"


class FakeStore:
    def __init__(self) -> None:
        self.batches: list[dict[str, Any]] = []
        self.atoms: list[tuple[dict[str, Any], str]] = []
        self.vector_updates: list[tuple[str, str, bool]] = []
        self.published: list[dict[str, Any]] = []

    def insert_batch(self, batch_id: str, request: dict, atom_count: int, errors: list[str], status: str) -> None:
        self.batches.append(
            {
                "batchId": batch_id,
                "mode": request.get("mode"),
                "atomCount": atom_count,
                "errors": list(errors),
                "status": status,
            }
        )

    def upsert_atom(self, atom: dict, reason: str) -> None:
        self.atoms.append((copy.deepcopy(atom), reason))

    def update_vector_status(self, atom_id: str, status: str, indexed: bool) -> None:
        self.vector_updates.append((atom_id, status, indexed))

    def published_atoms(self) -> list[dict]:
        return copy.deepcopy(self.published)


class FakeQdrant:
    def __init__(self, results: list[bool] | None = None) -> None:
        self.results = list(results or [])
        self.upserted: list[dict[str, Any]] = []

    def upsert(self, atom: dict) -> bool:
        self.upserted.append(copy.deepcopy(atom))
        return self.results.pop(0) if self.results else True

    def search(self, query: str, categories: list[str], exclude: list[str], limit: int) -> list:
        return []


class QuestionBankImportContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.store = FakeStore()
        self.qdrant = FakeQdrant()
        self.service = QuestionBankService(Settings(), self.store, self.qdrant)

    def test_validate_import_fixtures(self) -> None:
        errors = load_json("golden-errors.json")

        self.assertEqual([], self.service.validate_import_package(load_json("valid-draft.json")))
        self.assertEqual(errors["emptyAtoms"], self.service.validate_import_package(load_json("invalid-empty-atoms.json")))
        self.assertEqual(
            errors["missingPrinciples"],
            self.service.validate_import_package(load_json("invalid-missing-principles.json")),
        )
        self.assertEqual(
            errors["duplicateId"],
            self.service.validate_import_package(load_json("invalid-duplicate-id.json")),
        )

    def test_dry_run_only_creates_batch(self) -> None:
        request = load_json("valid-draft.json")
        request["mode"] = "DRY_RUN"

        result = self.service.import_batch(request)

        self.assertEqual(
            {
                "batchId": "qb-contract-draft",
                "mode": "DRY_RUN",
                "received": 1,
                "imported": 0,
                "published": 0,
                "failed": 0,
                "errors": [],
            },
            result,
        )
        self.assertEqual("CREATED", self.store.batches[0]["status"])
        self.assertEqual([], self.store.atoms)
        self.assertEqual([], self.qdrant.upserted)

    def test_draft_import_matches_golden_atom(self) -> None:
        result = self.service.import_batch(load_json("valid-draft.json"))

        self.assertEqual(1, result["imported"])
        self.assertEqual(0, result["published"])
        self.assertEqual(0, result["failed"])
        atom, reason = self.store.atoms[0]
        golden = load_json("golden-atom.json")
        self.assertEqual("import:qb-contract-draft", reason)
        self.assertEqual(golden["atomId"], atom["atom_id"])
        self.assertEqual(golden["subject"], atom["subject"])
        self.assertEqual(golden["category"], atom["category"])
        self.assertEqual(golden["difficulty"], atom["difficulty"])
        self.assertEqual(golden["tagsJson"], atom["tags_json"])
        self.assertEqual(golden["principles"], atom["principles"])
        self.assertEqual(golden["pitfalls"], atom["pitfalls"])
        self.assertEqual(golden["followUpPathsJson"], atom["follow_up_paths_json"])
        self.assertEqual(golden["status"], atom["status"])
        self.assertEqual(golden["sourceRef"], atom["source_ref"])
        self.assertEqual(golden["checksum"], atom["checksum"])
        self.assertEqual(golden["vectorStatus"], atom["vector_status"])

    def test_auto_publish_syncs_qdrant(self) -> None:
        result = self.service.import_batch(load_json("valid-auto-publish.json"))

        self.assertEqual(1, result["imported"])
        self.assertEqual(1, result["published"])
        self.assertEqual(0, result["failed"])
        self.assertEqual("PUBLISHED", self.qdrant.upserted[0]["status"])
        self.assertEqual("PENDING", self.qdrant.upserted[0]["vectorStatus"])
        self.assertEqual([("contract-java-hashmap", "SYNCED", True)], self.store.vector_updates)

    def test_reindex_counts_successful_published_syncs(self) -> None:
        self.qdrant = FakeQdrant([True, False])
        self.service = QuestionBankService(Settings(), self.store, self.qdrant)
        self.store.published = [
            {"atomId": "contract-java-hashmap", "status": "PUBLISHED"},
            {"atomId": "contract-java-jvm", "status": "PUBLISHED"},
        ]

        self.assertEqual(1, self.service.reindex_published_atoms())
        self.assertEqual(
            [
                ("contract-java-hashmap", "SYNCED", True),
                ("contract-java-jvm", "FAILED", False),
            ],
            self.store.vector_updates,
        )


def load_json(name: str) -> dict:
    return json.loads((CONTRACT_DIR / name).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
