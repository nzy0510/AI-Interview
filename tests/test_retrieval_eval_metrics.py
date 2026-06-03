import unittest

from scripts.retrieval_eval.calculate_metrics import (
    hit_rate_at_k,
    ndcg_at_k,
    paired_bootstrap_ci,
    recall_at_k,
)


class RetrievalEvalMetricsTest(unittest.TestCase):
    def test_recall_at_k_uses_relevance_two_or_above(self):
        judgments = {"a": 3, "b": 2, "c": 1}
        ranking = ["a", "x", "c", "b"]

        self.assertEqual(recall_at_k(ranking, judgments, 3), 0.5)
        self.assertEqual(recall_at_k(ranking, judgments, 4), 1.0)

    def test_hit_rate_at_k_counts_queries_with_at_least_one_relevant_atom(self):
        rankings = {"q1": ["a"], "q2": ["x"]}
        judgments = {"q1": {"a": 2}, "q2": {"b": 3}}

        self.assertEqual(hit_rate_at_k(rankings, judgments, 1), 0.5)

    def test_ndcg_at_k_uses_graded_relevance(self):
        judgments = {"a": 3, "b": 2}

        self.assertAlmostEqual(ndcg_at_k(["a", "b"], judgments, 2), 1.0)
        self.assertLess(ndcg_at_k(["b", "a"], judgments, 2), 1.0)

    def test_paired_bootstrap_is_deterministic_with_seed(self):
        baseline = [0.0, 0.5, 1.0]
        candidate = [0.5, 1.0, 1.0]

        first = paired_bootstrap_ci(baseline, candidate, seed=7, samples=200)
        second = paired_bootstrap_ci(baseline, candidate, seed=7, samples=200)

        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
