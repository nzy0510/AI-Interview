import unittest

from scripts.retrieval_eval.prelabel_candidates import merge_suggestions


class RetrievalEvalPrelabelTest(unittest.TestCase):
    def test_merge_suggestions_preserves_existing_human_judgment(self):
        candidates = [
            {
                "atom_id": "a",
                "source_ranks": {"all-minilm": 1},
                "human_judgment": {"relevance": 2, "reason": "人工已审核"},
            },
            {"atom_id": "b", "source_ranks": {"bge-m3": 1}},
        ]
        suggestions = {
            "a": {"relevance": 0, "reason": "模型不应覆盖", "confidence": 0.5},
            "b": {"relevance": 3, "reason": "直接追问", "confidence": 0.9},
        }

        rows = merge_suggestions("q1", candidates, suggestions)

        by_id = {row["atom_id"]: row for row in rows}
        self.assertEqual(by_id["a"]["human_judgment"]["relevance"], 2)
        self.assertEqual(by_id["b"]["model_suggestion"]["relevance"], 3)
        self.assertIsNone(by_id["b"]["human_judgment"])


if __name__ == "__main__":
    unittest.main()
