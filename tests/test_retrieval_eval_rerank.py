import unittest

from scripts.retrieval_eval.rerank_candidates import rerank_rows


class RetrievalEvalRerankTest(unittest.TestCase):
    def test_reranks_source_model_candidates_with_scorer(self):
        queries = [{"query_id": "q1", "query_text": "什么是 RAG？"}]
        atoms = [
            {"atom_id": "a", "search_text": "无关内容"},
            {"atom_id": "b", "search_text": "RAG 检索增强生成"},
        ]
        rankings = [
            {
                "query_id": "q1",
                "model": "multilingual-e5-base",
                "results": [
                    {"atom_id": "a", "rank": 1, "score": 0.9},
                    {"atom_id": "b", "rank": 2, "score": 0.8},
                ],
            }
        ]

        rows = rerank_rows(
            queries,
            atoms,
            rankings,
            source_model="multilingual-e5-base",
            output_model="multilingual-e5-base+test-reranker",
            candidate_top_k=20,
            score_pairs=lambda pairs: [0.1, 0.9],
        )

        self.assertEqual(rows[0]["model"], "multilingual-e5-base+test-reranker")
        self.assertEqual([result["atom_id"] for result in rows[0]["results"]], ["b", "a"])
        self.assertEqual([result["rank"] for result in rows[0]["results"]], [1, 2])
        self.assertEqual(rows[0]["results"][0]["source_rank"], 2)

    def test_ignores_rankings_from_other_models(self):
        rows = rerank_rows(
            [{"query_id": "q1", "query_text": "query"}],
            [{"atom_id": "a", "search_text": "text"}],
            [{"query_id": "q1", "model": "all-minilm", "results": [{"atom_id": "a"}]}],
            source_model="multilingual-e5-base",
            output_model="reranked",
            candidate_top_k=20,
            score_pairs=lambda pairs: [1.0],
        )

        self.assertEqual(rows, [])


if __name__ == "__main__":
    unittest.main()
