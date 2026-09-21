#!/usr/bin/env python3
"""Restore a backup into disposable local containers; never launch the application."""
import argparse
import contextlib
import json
import os
import re
from pathlib import Path, PurePosixPath
import secrets
import shutil
import subprocess
import tarfile
import tempfile
import time
import uuid

from interwise_ops import OpsError, digest, read_env, write_json


def extract_safe(archive, target):
    with tarfile.open(archive, "r:gz") as stream:
        members = stream.getmembers()
        for entry in members:
            path = PurePosixPath(entry.name)
            if (path.is_absolute() or ".." in path.parts or "\\" in entry.name
                    or ":" in entry.name or not (entry.isfile() or entry.isdir())):
                raise OpsError("Unsafe archive member")
            if not (target / entry.name).resolve().is_relative_to(target.resolve()):
                raise OpsError("Archive member escapes target")
        for entry in members:
            dest = target / entry.name
            if entry.isdir():
                dest.mkdir(parents=True, exist_ok=True)
            else:
                dest.parent.mkdir(parents=True, exist_ok=True)
                with stream.extractfile(entry) as source, dest.open("xb") as output:
                    shutil.copyfileobj(source, output)


def verify_manifest(root):
    file = root / "manifest.json"
    if not file.exists():
        file = root / "receipt.json"
    manifest = json.loads(file.read_text(encoding="utf-8"))
    for entry in manifest["files"]:
        path = root / entry["name"]
        if not path.resolve().is_relative_to(root.resolve()):
            raise OpsError("Manifest path escapes backup")
        if path.stat().st_size != entry["bytes"] or digest(path) != entry["sha256"]:
            raise OpsError("Backup checksum mismatch")
    return len(manifest["files"])


def qdrant_contract(info):
    # Metadata is observed before snapshot creation; concurrent writes can change counts.
    params = info["config"]["params"]
    return {
        "vectors": params["vectors"],
        "sparse_vectors": params.get("sparse_vectors"),
        "payload_indexes": {
            name: {key: value for key, value in index.items() if key != "points"}
            for name, index in info.get("payload_schema", {}).items()
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="Plain tar.gz or age encrypted archive")
    parser.add_argument("--workspace", type=Path, required=True, help="Private scratch directory outside tracked files")
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--context", default="desktop-linux")
    parser.add_argument("--age")
    parser.add_argument("--identity", type=Path)
    parser.add_argument("--backend-image", default="interwise/backend:20260918-public1")
    args = parser.parse_args()
    os.umask(0o077)
    args.workspace.mkdir(parents=True, exist_ok=True)
    token = "interwise-restore-" + uuid.uuid4().hex[:12]
    docker = ["docker", "--context", args.context]
    owned = []

    def command(parts, **kwargs):
        result = subprocess.run(docker + parts, stderr=subprocess.PIPE, timeout=180, **kwargs)
        if result.returncode:
            code = re.search(rb"ERROR [0-9]+ \([A-Z0-9]+\)", result.stderr)
            raise OpsError("Isolated Docker " + parts[0] + " failed" + (": " + code.group().decode() if code else ""))
        return result

    started = time.time()
    receipt = {"archive_sha256": digest(args.archive), "started_at": started, "passed": False}

    def create(parts):
        container_id = command(["create", *parts], stdout=subprocess.PIPE).stdout.decode().strip()
        if not re.fullmatch(r"[a-f0-9]{64}", container_id):
            raise OpsError("Docker returned an invalid container ID")
        owned.append(container_id)
        return container_id

    def cleanup():
        errors = []
        for container_id in reversed(owned):
            try:
                command(["rm", "-f", "-v", container_id], stdout=subprocess.DEVNULL)
            except Exception:
                errors.append(container_id)
        if errors:
            receipt.update(passed=False, cleanup_errors=errors)

    try:
        with contextlib.ExitStack() as stack:
            temporary = stack.enter_context(tempfile.TemporaryDirectory(prefix=token + "-", dir=args.workspace,
                                                                        ignore_cleanup_errors=True))
            # Containers release bind mounts before private scratch files are removed.
            stack.callback(cleanup)
            stage = Path(temporary)
            source = args.archive
            if source.suffix == ".age":
                if not args.age or not args.identity:
                    raise OpsError("Encrypted restore requires age and an identity file")
                source = stage / "decrypted.tar.gz"
                result = subprocess.run([args.age, "-d", "-i", str(args.identity), "-o", str(source), str(args.archive)],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=180)
                if result.returncode:
                    raise OpsError("Backup decryption failed")
            extracted = stage / "backup"
            extracted.mkdir()
            extract_safe(source, extracted)
            roots = [extracted] if (extracted / "manifest.json").exists() else list(extracted.iterdir())
            if len(roots) != 1 or not roots[0].is_dir():
                raise OpsError("Unexpected backup structure")
            root = roots[0]
            receipt["verified_files"] = verify_manifest(root)
            if (root / "manifest.json").exists():
                manifest = json.loads((root / "manifest.json").read_text())
                required = {"mysql.sql", "redis.rdb", "qdrant.snapshot", "qdrant.json",
                            "application-files.tar.gz", "caddy-files.tar.gz"}
                if manifest.get("format") != 1 or not required.issubset({x["name"] for x in manifest["files"]}):
                    raise OpsError("Current backup manifest is missing required verified components")
            files = stage / "application"
            files.mkdir()
            extract_safe(root / "application-files.tar.gz", files)
            receipt["application_files_verified"] = True
            if (root / "caddy-files.tar.gz").exists():
                caddy = stage / "caddy"
                caddy.mkdir()
                extract_safe(root / "caddy-files.tar.gz", caddy)
                receipt["caddy_files_verified"] = True
            # The pre-public backup kept env files at its top level.
            env_root = files if (files / ".env.prod").exists() else root
            env = read_env(env_root)
            database = env["DB_NAME"]
            if not re.fullmatch(r"[A-Za-z0-9_]+", database):
                raise OpsError("Unsupported database name")
            password = secrets.token_urlsafe(32)
            db_env = stage / "restore.env"
            db_env.write_text("MYSQL_ROOT_PASSWORD=" + password + "\nMYSQL_DATABASE=" + database + "\n")
            receipt["stage"] = "mysql_start"
            name = token + "-mysql"
            create(["--pull", "never", "--name", name, "--network", "none",
                    "--label", "interwise.restore=" + token, "--memory", "768m", "--env-file", str(db_env),
                    "mysql:8.0", "--innodb-buffer-pool-size=128M"])
            command(["start", name], stdout=subprocess.DEVNULL)
            deadline = time.time() + 120
            while time.time() < deadline:
                result = subprocess.run(docker + ["exec", name, "sh", "-c",
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot "$MYSQL_DATABASE" -e "SELECT 1"'],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
                if result.returncode == 0:
                    break
                time.sleep(2)
            else:
                raise OpsError("Restore MySQL did not become ready")
            receipt["stage"] = "mysql_import"
            mysql = ["exec", "-i", name, "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$MYSQL_DATABASE"']
            with (root / "mysql.sql").open("rb") as sql:
                command(mysql, stdin=sql, stdout=subprocess.DEVNULL)

            def query(sql):
                return command(mysql, input=sql.encode(), stdout=subprocess.PIPE).stdout.decode().strip()

            tables = query("SHOW TABLES;").splitlines()
            counts = {table: int(query("SELECT COUNT(*) FROM `" + table.replace("`", "``") + "`;")) for table in tables}
            receipt["table_counts"] = counts
            for table in tables:
                result = query("CHECK TABLE `" + table.replace("`", "``") + "`;")
                if not result.endswith("\tstatus\tOK"):
                    raise OpsError("Restored table integrity check failed")
            receipt["table_integrity_checks"] = True
            if (root / "data-counts.json").exists():
                baseline = json.loads((root / "data-counts.json").read_text())
                checks = {"users": counts["user"], "llmConfigs": counts["user_llm_config"],
                          "interviews": counts["interview_record"],
                          "publicAtoms": int(query("SELECT COUNT(*) FROM knowledge_atom WHERE scope='PUBLIC';"))}
                for key, value in checks.items():
                    if key in baseline and value != baseline[key]:
                        raise OpsError("Restored baseline count differs: " + key)
                receipt["baseline_matches"] = True
            ciphertexts = query("SELECT encrypted_api_key FROM user_llm_config ORDER BY id;")
            receipt["stage"] = "provider_crypto"
            crypto_input = env["APP_LLM_CONFIG_ENCRYPTION_KEY"] + "\n" + (ciphertexts + "\n" if ciphertexts else "")
            helper = Path(__file__).with_name("VerifyProviderKeys.java").resolve()
            crypto_id = create(["--pull", "never", "--network", "none", "-i", "--name", token + "-crypto",
                                "--label", "interwise.restore=" + token,
                                "--mount", "type=bind,source=" + str(helper) + ",target=/tmp/VerifyProviderKeys.java,readonly",
                                "--entrypoint", "java", args.backend_image, "/tmp/VerifyProviderKeys.java"])
            crypto = command(["start", "-a", "-i", crypto_id], input=crypto_input.encode(), stdout=subprocess.PIPE)
            receipt["provider_crypto"] = json.loads(crypto.stdout)
            receipt["redis_restored"] = False
            receipt["stage"] = "redis"
            if (root / "redis.rdb").exists():
                redis_name = token + "-redis"
                create(["--pull", "never", "--network", "none", "--name", redis_name,
                        "--label", "interwise.restore=" + token,
                        "--mount", "type=bind,source=" + str(root / "redis.rdb") + ",target=/restore/dump.rdb,readonly",
                        "redis:7-alpine", "sh", "-c", "cp /restore/dump.rdb /data/dump.rdb && exec redis-server --appendonly no"])
                command(["start", redis_name], stdout=subprocess.DEVNULL)
                deadline = time.time() + 30
                while time.time() < deadline:
                    ping = subprocess.run(docker + ["exec", redis_name, "redis-cli", "ping"], capture_output=True, timeout=10)
                    if ping.returncode == 0 and b"PONG" in ping.stdout:
                        break
                    time.sleep(1)
                else:
                    raise OpsError("Redis RDB restore failed")
                receipt["redis_restored"] = True
                receipt["redis_current_keys"] = int(command(["exec", redis_name, "redis-cli", "dbsize"], stdout=subprocess.PIPE).stdout)
            receipt["qdrant_restored"] = False
            receipt["stage"] = "qdrant"
            if (root / "qdrant.snapshot").exists():
                metadata = json.loads((root / "qdrant.json").read_text())
                qname = token + "-qdrant"
                create(["--pull", "never", "--name", qname, "--network", "none",
                        "--label", "interwise.restore=" + token,
                        "--mount", "type=bind,source=" + str(root / "qdrant.snapshot") + ",target=/restore/qdrant.snapshot,readonly",
                        "qdrant/qdrant:v" + metadata["version"], "./qdrant", "--snapshot", "/restore/qdrant.snapshot:restore_atoms"])
                command(["start", qname], stdout=subprocess.DEVNULL)
                http_helper = Path(__file__).with_name("ReadQdrant.java").resolve()

                def api(path, body=None):
                    probe = create(["--pull", "never", "--network", "container:" + qname, "-i",
                                    "--label", "interwise.restore=" + token,
                                    "--mount", "type=bind,source=" + str(http_helper) + ",target=/tmp/ReadQdrant.java,readonly",
                                    "--entrypoint", "java", args.backend_image, "/tmp/ReadQdrant.java",
                                    "/collections/restore_atoms" + path])
                    try:
                        result = command(["start", "-a", "-i", probe],
                                         input=json.dumps(body).encode() if body else b"", stdout=subprocess.PIPE)
                        return json.loads(result.stdout)["result"]
                    finally:
                        try:
                            command(["rm", "-f", "-v", probe], stdout=subprocess.DEVNULL)
                        except Exception:
                            pass  # Still registered for the final cleanup retry.
                        else:
                            owned.remove(probe)

                deadline = time.time() + 90
                while time.time() < deadline:
                    try:
                        info = api("")
                        if info["status"] == "green":
                            break
                    except Exception:
                        pass
                    time.sleep(2)
                else:
                    raise OpsError("Qdrant snapshot restore failed")
                if qdrant_contract(info) != qdrant_contract(metadata["info"]):
                    raise OpsError("Qdrant restored vector or payload index configuration mismatch")
                if info["points_count"]:
                    point = api("/points/scroll", {"limit": 1, "with_vector": True, "with_payload": True})["points"][0]
                    found = api("/points/query", {"query": point["vector"], "limit": 1, "with_payload": True,
                                                 "filter": {"must": [{"has_id": [point["id"]]}]}})["points"][0]
                    if found["id"] != point["id"] or found["score"] < 0.99:
                        raise OpsError("Qdrant restored vector self-query failed")
                receipt.update(qdrant_restored=True, qdrant_points=info["points_count"],
                               qdrant_observed_points_before_snapshot=metadata["info"]["points_count"],
                               qdrant_vector_query=bool(info["points_count"]))
            complete = receipt["redis_restored"] and receipt["qdrant_restored"]
            if (root / "manifest.json").exists() and not complete:
                raise OpsError("Current backup format is missing required Redis or Qdrant components")
            receipt.update(passed=True, elapsed_seconds=round(time.time() - started, 2),
                           coverage="complete" if complete else "legacy_database_only",
                           app_started=False, external_model_calls=0, stage="complete")
    except Exception as error:
        receipt["error"] = str(error) if isinstance(error, OpsError) else type(error).__name__
        raise
    finally:
        if "temporary" in locals() and Path(temporary).exists():
            receipt.update(passed=False, scratch_cleanup_required=temporary)
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        write_json(args.receipt, receipt)
    if not receipt["passed"]:
        raise OpsError("Restore checks completed but temporary resources need cleanup; see receipt")
    print(json.dumps({key: value for key, value in receipt.items() if key != "table_counts"}))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error) if isinstance(error, OpsError) else "Restore failed: " + type(error).__name__)
        raise SystemExit(1)
