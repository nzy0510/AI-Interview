import importlib.util
import io
import json
import pathlib
import sys
import tarfile
import tempfile
import unittest
from email.utils import parsedate_to_datetime
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("interwise_ops", ROOT / "scripts/ops/interwise_ops.py")
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)
sys.modules["interwise_ops"] = ops
restore_spec = importlib.util.spec_from_file_location("restore_rehearsal", ROOT / "scripts/ops/restore_rehearsal.py")
restore = importlib.util.module_from_spec(restore_spec)
restore_spec.loader.exec_module(restore)


class HealthAndAlertsTest(unittest.TestCase):
    def test_http_200_degraded_or_missing_dependency_is_not_healthy(self):
        data = dict.fromkeys(("app", "mysql", "redis", "qdrant", "status"), "UP")
        self.assertTrue(ops.healthy({"data": data}))
        for key in data:
            with self.subTest(key=key):
                self.assertFalse(ops.healthy({"data": {**data, key: "DOWN"}}))
                self.assertFalse(ops.healthy({"data": {k: v for k, v in data.items() if k != key}}))

    def test_alert_deduplicates_then_repeats_and_recovers_once(self):
        send, state = ops.alert_transition({}, ["health"], 10000)
        self.assertTrue(send)
        send, state = ops.alert_transition(state, ["health"], 10300)
        self.assertFalse(send)
        send, state = ops.alert_transition(state, ["health"], 13601)
        self.assertTrue(send)
        send, state = ops.alert_transition(state, [], 13700)
        self.assertTrue(send)
        self.assertFalse(ops.alert_transition(state, [], 13800)[0])

    def test_initial_healthy_run_sends_no_email(self):
        self.assertFalse(ops.alert_transition({}, [], 10000)[0])

    def test_notification_has_date_and_message_id_for_mailbox_display(self):
        env = {"MAIL_HOST": "smtp.example.test", "MAIL_PORT": "587",
               "MAIL_USERNAME": "sender@example.test", "MAIL_PASSWORD": "test-fixture"}
        with patch.object(ops, "read_env", return_value=env), patch.object(ops.smtplib, "SMTP") as smtp:
            ops.notify({"root": "/unused", "alert_email": "owner@example.test"}, "TEST", "Delivery check")
        message = smtp.return_value.send_message.call_args.args[0]
        self.assertIsNotNone(message["Date"], "Notification must have a date for mailbox sorting")
        self.assertIsNotNone(parsedate_to_datetime(message["Date"]).tzinfo)
        self.assertRegex(message["Message-ID"], r"^<[^<>\s]+@example\.test>$")
        self.assertEqual(message["To"], "owner@example.test")

    def test_retention_preserves_manual_and_partial_archives(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            names = ["interwise-20260921T000000Z.tar.gz.age", "interwise-20260921T060000Z.tar.gz.age",
                     "interwise-20260921T120000Z.tar.gz.age", "pre-public-backup.tar.gz",
                     "interwise-manual.tar.gz.age", "interwise-20260921T180000Z.tar.gz.age.partial"]
            for name in names:
                (root / name).write_bytes(b"fixture")
            ops.retain_local(root)
            self.assertEqual(set(p.name for p in root.iterdir()), set(names[1:]))

    def test_missing_external_env_is_recorded_as_a_failed_attempt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            (root / ".env.prod").write_text("MAIL_HOST=example.test\n")
            with self.assertRaises(ops.OpsError):
                ops.backup({"root": temp, "state_dir": temp})
            state = json.loads((root / "backup-state.json").read_text())
            self.assertFalse(state["last_attempt_ok"])
            self.assertEqual(state["stage"], "configuration")
            self.assertEqual(ops.read_env(root, names=(".env.prod",))["MAIL_HOST"], "example.test")

    def test_truncated_snapshot_is_rejected_and_remote_identity_preserved(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            registry = root / "state.json"
            env = {"QDRANT_URL": "https://qdrant.example", "QDRANT_API_KEY": "fixture", "QDRANT_COLLECTION": "test"}
            responses = [{"result": {}}, {"version": "1.19.1"}, {"result": {"name": "owned.snapshot", "size": 100}}]
            with patch.object(ops, "request_json", side_effect=responses), patch.object(ops.urllib.request, "urlopen", return_value=io.BytesIO(b"short")):
                with self.assertRaisesRegex(ops.OpsError, "size mismatch"):
                    ops.backup_qdrant(env, root, registry)
            self.assertEqual(len(json.loads(registry.read_text())["snapshots"]), 1)

    def test_uncertain_snapshot_post_is_not_repeated(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            registry = root / "state.json"
            env = {"QDRANT_URL": "https://qdrant.example", "QDRANT_API_KEY": "fixture", "QDRANT_COLLECTION": "test"}
            with patch.object(ops, "request_json", side_effect=[{"result": {}}, {}, TimeoutError()]):
                with self.assertRaises(TimeoutError):
                    ops.backup_qdrant(env, root, registry)
            with patch.object(ops, "request_json", side_effect=[{"result": {}}, {}]) as request:
                with self.assertRaisesRegex(ops.OpsError, "uncertain"):
                    ops.backup_qdrant(env, root, registry)
                self.assertEqual(request.call_count, 2)  # Only metadata GETs, no second POST.


class RestoreArchiveTest(unittest.TestCase):
    def test_qdrant_contract_ignores_live_counts_but_checks_vector_and_index_types(self):
        before = {"points_count": 10, "config": {"params": {"vectors": {"size": 1024, "distance": "Cosine"}}},
                  "payload_schema": {"scope": {"data_type": "keyword", "points": 10}}}
        restored = json.loads(json.dumps(before))
        restored["points_count"] = 11
        restored["payload_schema"]["scope"]["points"] = 11
        self.assertEqual(restore.qdrant_contract(before), restore.qdrant_contract(restored))
        restored["payload_schema"]["scope"]["data_type"] = "text"
        self.assertNotEqual(restore.qdrant_contract(before), restore.qdrant_contract(restored))
        restored = json.loads(json.dumps(before))
        restored["config"]["params"]["vectors"]["size"] = 768
        self.assertNotEqual(restore.qdrant_contract(before), restore.qdrant_contract(restored))

    def test_traversal_absolute_paths_and_links_are_rejected(self):
        for name, kind in [("../outside", tarfile.REGTYPE), ("/outside", tarfile.REGTYPE),
                           ("C:/outside", tarfile.REGTYPE), ("..\\outside", tarfile.REGTYPE),
                           ("link", tarfile.SYMTYPE), ("link", tarfile.LNKTYPE)]:
            with self.subTest(name=name, kind=kind), tempfile.TemporaryDirectory() as temp:
                root = pathlib.Path(temp)
                archive = root / "backup.tar.gz"
                with tarfile.open(archive, "w:gz") as stream:
                    entry = tarfile.TarInfo(name)
                    entry.type = kind
                    stream.addfile(entry)
                dest = root / "extracted"
                dest.mkdir()
                with self.assertRaises(ops.OpsError):
                    restore.extract_safe(archive, dest)
                self.assertEqual(list(dest.iterdir()), [])

    def test_modified_backup_content_fails_checksum_verification(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            data = root / "mysql.sql"
            data.write_bytes(b"original")
            ops.write_json(root / "manifest.json", {"files": [{"name": data.name, "bytes": data.stat().st_size,
                                                               "sha256": ops.digest(data)}]})
            data.write_bytes(b"tampered")
            with self.assertRaisesRegex(ops.OpsError, "checksum"):
                restore.verify_manifest(root)


if __name__ == "__main__":
    unittest.main()
