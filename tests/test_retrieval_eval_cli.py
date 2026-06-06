import subprocess
import sys
import unittest
from pathlib import Path


class RetrievalEvalCliTest(unittest.TestCase):
    def test_scripts_support_direct_help_invocation(self):
        root = Path(__file__).resolve().parents[1]
        scripts = [
            "export_atoms.py",
            "extract_real_queries.py",
            "generate_synthetic_queries.py",
            "score_embeddings.py",
            "build_candidate_pool.py",
            "prelabel_candidates.py",
            "calculate_metrics.py",
            "validate_dataset.py",
            "rerank_candidates.py",
        ]

        for script in scripts:
            with self.subTest(script=script):
                result = subprocess.run(
                    [sys.executable, str(root / "scripts" / "retrieval_eval" / script), "--help"],
                    cwd=root,
                    capture_output=True,
                    text=True,
                    timeout=10,
                    check=False,
                )
                self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
