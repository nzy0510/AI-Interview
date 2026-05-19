from __future__ import annotations

import sys
import unittest
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
MCP_SKILL_ROOT = REPO_ROOT / "services" / "mcp-skill"
sys.path.insert(0, str(MCP_SKILL_ROOT))

from mcp_server.auth import AuthContext  # noqa: E402
from mcp_server.config import Settings  # noqa: E402
from mcp_server.protocol import McpDispatcher  # noqa: E402


class FakeQuestionBankService:
    def search_public(self, arguments: dict[str, Any]) -> list[dict]:
        return [{"atomId": "a1", "subject": "HashMap"}]


class FakeQuotaStore:
    def __init__(self) -> None:
        self.consumed: list[tuple[int, dict[str, int]]] = []

    def mcp_quota_limits(self, role: str) -> dict[str, int]:
        if role == "developer":
            return {"total": 77, "search": 33}
        return {"total": 7, "search": 3}

    def consume_mcp_quota(self, user_id: int, quota_limits: dict[str, int]) -> dict:
        self.consumed.append((user_id, dict(quota_limits)))
        return {}

    def log_mcp_call(self, **kwargs: Any) -> None:
        return None


class McpQuotaPolicyTest(unittest.TestCase):
    def test_public_call_uses_database_quota_policy(self) -> None:
        store = FakeQuotaStore()
        dispatcher = McpDispatcher(FakeQuestionBankService(), Settings(), store)

        dispatcher.call_tool(
            "search_interview_atoms",
            {"query": "HashMap 扩容", "limit": 5},
            AuthContext(role="read", user_id=42),
        )

        self.assertEqual([(42, {"total": 7, "search": 3})], store.consumed)

    def test_developer_call_uses_developer_policy(self) -> None:
        store = FakeQuotaStore()
        dispatcher = McpDispatcher(FakeQuestionBankService(), Settings(), store)

        dispatcher.call_tool(
            "search_interview_atoms",
            {"query": "HashMap 扩容", "limit": 5},
            AuthContext(role="developer", user_id=1),
        )

        self.assertEqual([(1, {"total": 77, "search": 33})], store.consumed)


if __name__ == "__main__":
    unittest.main()
