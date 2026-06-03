import unittest

from scripts.retrieval_eval.common import anonymize_text, build_atom_text, dedupe_queries


class RetrievalEvalCommonTest(unittest.TestCase):
    def test_anonymize_text_removes_common_personal_identifiers(self):
        raw = "我叫张三，邮箱 zhangsan@example.com，电话 13800138000。"

        cleaned = anonymize_text(raw)

        self.assertNotIn("张三", cleaned)
        self.assertNotIn("zhangsan@example.com", cleaned)
        self.assertNotIn("13800138000", cleaned)

    def test_build_atom_text_matches_production_search_content(self):
        atom = {
            "subject": "注意力机制",
            "principles": "核心原理",
            "pitfalls": "常见错误",
            "follow_up_paths": ["追问一", "追问二"],
        }

        text = build_atom_text(atom)

        self.assertIn("考核点: 注意力机制", text)
        self.assertIn("核心原理与标准答案: 核心原理", text)
        self.assertIn("推荐的深度追问路径", text)

    def test_dedupe_queries_keeps_first_normalized_query(self):
        rows = [
            {"query_text": "什么是 RAG？"},
            {"query_text": "  什么是  RAG？ "},
        ]

        self.assertEqual(len(dedupe_queries(rows)), 1)


if __name__ == "__main__":
    unittest.main()
