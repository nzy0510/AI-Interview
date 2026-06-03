import unittest

from scripts.retrieval_eval.generate_synthetic_queries import (
    remaining_quotas,
    validate_real_query_scenarios,
)


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


if __name__ == "__main__":
    unittest.main()
