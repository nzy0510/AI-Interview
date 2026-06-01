import datetime as dt
import unittest

from scripts.question_bank_import import default_batch_id, default_output_path


class QuestionBankImportNamingTest(unittest.TestCase):
    def test_default_batch_id_includes_category_mode_timestamp_and_suffix(self):
        batch_id = default_batch_id(
            category="Java",
            mode="DRY_RUN",
            generated_at=dt.datetime(2026, 6, 1, 12, 34, 56, tzinfo=dt.timezone.utc),
            suffix="abc123",
        )

        self.assertEqual(batch_id, "qb-java-dry-run-20260601-123456-abc123")

    def test_default_output_path_uses_batch_id_as_file_name(self):
        output_path = default_output_path("qb-java-draft-20260601-123456-abc123")

        self.assertEqual(
            output_path.as_posix(),
            "question_bank_imports/qb-java-draft-20260601-123456-abc123.json",
        )

    def test_default_batch_id_keeps_non_ascii_category_meaning(self):
        batch_id = default_batch_id(
            category="HR软技能",
            mode="DRAFT",
            generated_at=dt.datetime(2026, 6, 1, 12, 34, 56, tzinfo=dt.timezone.utc),
            suffix="abc123",
        )

        self.assertEqual(batch_id, "qb-hr软技能-draft-20260601-123456-abc123")


if __name__ == "__main__":
    unittest.main()
