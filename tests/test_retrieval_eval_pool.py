import unittest

from scripts.retrieval_eval.build_candidate_pool import merge_candidates, tokenize
from scripts.retrieval_eval.score_embeddings import MODEL_CONFIGS


class RetrievalEvalPoolTest(unittest.TestCase):
    def test_merge_candidates_deduplicates_atoms_and_preserves_sources(self):
        rankings = {
            "all-minilm": [{"atom_id": "a", "rank": 1}, {"atom_id": "b", "rank": 2}],
            "bge-m3": [{"atom_id": "a", "rank": 3}, {"atom_id": "c", "rank": 1}],
        }

        merged = merge_candidates(rankings)

        by_id = {row["atom_id"]: row for row in merged}
        self.assertEqual(set(by_id), {"a", "b", "c"})
        self.assertEqual(set(by_id["a"]["sources"]), {"all-minilm", "bge-m3"})

    def test_tokenize_includes_chinese_bigrams_and_latin_terms(self):
        tokens = tokenize("RAG 检索增强生成")

        self.assertIn("rag", tokens)
        self.assertIn("检索", tokens)
        self.assertIn("增强", tokens)

    def test_multilingual_e5_uses_query_and_passage_prefixes(self):
        config = MODEL_CONFIGS["multilingual-e5-base"]

        self.assertEqual(config["query_prefix"], "query: ")
        self.assertEqual(config["passage_prefix"], "passage: ")


if __name__ == "__main__":
    unittest.main()
