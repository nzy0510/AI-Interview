import unittest

from scripts.retrieval_eval.generate_synthetic_queries import (
    remaining_quotas,
    validate_real_query_scenarios,
)
from scripts.retrieval_eval.validate_dataset import validate_atom_snapshot, validate_dataset_rows


class RetrievalEvalDatasetTest(unittest.TestCase):
    def test_remaining_quotas_subtract_real_query_scenarios(self):
        real_rows = [
            {"scenario": "correct_but_incomplete"},
            {"scenario": "correct_but_incomplete"},
            {"scenario": "concept_confusion"},
        ]

        quotas = remaining_quotas(real_rows)

        self.assertEqual(quotas["correct_but_incomplete"], 23)
        self.assertEqual(quotas["concept_confusion"], 19)

    def test_real_queries_require_reviewed_scenario_before_generation(self):
        with self.assertRaisesRegex(ValueError, "scenario"):
            validate_real_query_scenarios([{"query_text": "有效真实 query", "scenario": None}])

    def test_atom_snapshot_requires_matching_search_text(self):
        atom = {
            "atom_id": "a",
            "subject": "注意力",
            "category": "AI大模型",
            "principles": "原理",
            "pitfalls": "",
            "follow_up_paths": [],
            "status": "PUBLISHED",
            "search_text": "wrong",
        }

        errors = validate_atom_snapshot([atom])

        self.assertTrue(any("search_text" in error for error in errors))

    def test_dataset_rows_require_reviewed_sources_and_known_atoms(self):
        rows = [
            {
                "query_id": "q1",
                "source": "synthetic_unreviewed",
                "scenario": "short_clear_topic",
                "query_text": "什么是注意力机制？ 我不太清楚。",
                "judgments": [{"atom_id": "missing", "relevance": 3, "reason": "直接相关"}],
            }
        ]

        errors = validate_dataset_rows(rows, {"a"}, expected_count=1)

        self.assertTrue(any("source" in error for error in errors))
        self.assertTrue(any("unknown atom" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
