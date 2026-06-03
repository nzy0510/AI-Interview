import unittest

from scripts.retrieval_eval.export_atoms import normalize_atom_row
from scripts.retrieval_eval.extract_real_queries import prepare_real_queries


class RetrievalEvalExportsTest(unittest.TestCase):
    def test_normalize_atom_row_parses_json_and_builds_search_text(self):
        row = {
            "atom_id": "attention",
            "subject": "注意力机制",
            "category": "AI大模型",
            "difficulty": "mid",
            "tags_json": '["Transformer"]',
            "principles": "核心原理",
            "pitfalls": "常见错误",
            "follow_up_paths_json": '["追问一"]',
            "status": "PUBLISHED",
        }

        atom = normalize_atom_row(row)

        self.assertEqual(atom["tags"], ["Transformer"])
        self.assertEqual(atom["follow_up_paths"], ["追问一"])
        self.assertIn("考核点: 注意力机制", atom["search_text"])

    def test_prepare_real_queries_anonymizes_deduplicates_and_removes_ids(self):
        rows = [
            {
                "request_id": "secret-id",
                "position": "AI大模型",
                "phase": "TECHNICAL",
                "query_text": "LoRA 为什么减少参数量？ 我叫张三，我只知道低秩矩阵。",
                "candidate_count": 0,
                "retrieval_strategy": "MYSQL_FALLBACK",
                "status": "SUCCESS",
                "create_time": "2026-06-03T12:00:00",
            },
            {
                "request_id": "other-id",
                "position": "AI大模型",
                "phase": "TECHNICAL",
                "query_text": "  LoRA 为什么减少参数量？ 我叫张三，我只知道低秩矩阵。 ",
                "candidate_count": 0,
                "retrieval_strategy": "MYSQL_FALLBACK",
                "status": "SUCCESS",
                "create_time": "2026-06-03T12:01:00",
            },
        ]

        queries = prepare_real_queries(rows, limit=40)

        self.assertEqual(len(queries), 1)
        self.assertNotIn("request_id", queries[0])
        self.assertNotIn("张三", queries[0]["query_text"])
        self.assertEqual(queries[0]["source"], "real_anonymized")
        self.assertIsNone(queries[0]["scenario"])


if __name__ == "__main__":
    unittest.main()
