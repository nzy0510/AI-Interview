#!/usr/bin/env python3
"""Small Linux-host backup and health checks. Credentials stay in private env files."""
import argparse
import contextlib
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import smtplib
import socket
import ssl
import subprocess
import tarfile
import tempfile
import time
import urllib.parse
import urllib.request
from email.message import EmailMessage


class OpsError(Exception):
    """Only non-sensitive, operator-facing messages belong here."""


def read_env(root, names=(".env.prod", ".env.external.prod")):
    values = {}
    for name in names:
        for line in (root / name).read_text(encoding="utf-8-sig").splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            key, value = line.split("=", 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[key.strip()] = value
    return values


def run(args, **kwargs):
    result = subprocess.run(args, stderr=subprocess.PIPE, timeout=1800, **kwargs)
    if result.returncode:
        # Raw tool errors can contain URLs, SQL and credentials.
        raise OpsError(Path(args[0]).name + " failed (exit " + str(result.returncode) + ")")
    return result


def write_json(path, data):
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temp.replace(path)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


@contextlib.contextmanager
def lock(path):
    import fcntl
    with path.open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise OpsError("Another invocation is running") from None
        yield


def request_json(url, headers=None, method="GET", timeout=30):
    req = urllib.request.Request(url, headers=headers or {}, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.load(response)


def backup_qdrant(env, stage, registry_path):
    base = env["QDRANT_URL"].rstrip("/")
    if not base.startswith("https://"):
        raise OpsError("Cloud Qdrant backup requires HTTPS")
    headers = {"api-key": env["QDRANT_API_KEY"]}
    collection = urllib.parse.quote(env["QDRANT_COLLECTION"], safe="")
    url = base + "/collections/" + collection
    info = request_json(url, headers)
    version = request_json(base + "/", headers).get("version")
    registry = json.loads(registry_path.read_text()) if registry_path.exists() else {"snapshots": []}
    if registry.get("uncertain_create"):
        raise OpsError("Previous Qdrant snapshot creation is uncertain; inspect before retrying")
    if len(registry["snapshots"]) >= 2:
        raise OpsError("Two pending Qdrant snapshots need inspection; no further snapshots created")
    # Do not retry an uncertain POST: the snapshot may already have been created.
    registry["uncertain_create"] = True
    write_json(registry_path, registry)
    snapshot = request_json(url + "/snapshots?wait=true", headers, "POST", 180)["result"]
    name = snapshot["name"]
    snap_url = url + "/snapshots/" + urllib.parse.quote(name, safe="")
    registry["snapshots"].append({"url": snap_url, "created_at": time.time()})
    registry["uncertain_create"] = False
    write_json(registry_path, registry)
    with urllib.request.urlopen(urllib.request.Request(snap_url, headers=headers), timeout=180) as response:
        with (stage / "qdrant.snapshot").open("wb") as output:
            shutil.copyfileobj(response, output)
    if not snapshot.get("size") or (stage / "qdrant.snapshot").stat().st_size != snapshot["size"]:
        raise OpsError("Qdrant snapshot size mismatch; remote snapshot preserved")
    if snapshot.get("checksum") and digest(stage / "qdrant.snapshot") != snapshot["checksum"]:
        raise OpsError("Qdrant snapshot checksum mismatch; remote snapshot preserved")
    write_json(stage / "qdrant.json", {"version": version, "collection": env["QDRANT_COLLECTION"],
                                      "info": info["result"], "snapshot": snapshot})


def cleanup_qdrant(env, registry_path):
    registry = json.loads(registry_path.read_text())
    prefix = env["QDRANT_URL"].rstrip("/") + "/collections/" + urllib.parse.quote(env["QDRANT_COLLECTION"], safe="") + "/snapshots/"
    for entry in list(registry["snapshots"]):
        if not entry["url"].startswith(prefix):
            raise OpsError("Pending snapshot belongs to a different Qdrant configuration")
        try:
            request_json(entry["url"], {"api-key": env["QDRANT_API_KEY"]}, method="DELETE", timeout=60)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        registry["snapshots"].remove(entry)
        write_json(registry_path, registry)


def retain_local(directory, keep=2):
    # Only finalized archives from this script; never touch deployment/manual backups.
    files = sorted(p for p in directory.glob("interwise-*.tar.gz.age")
                   if re.fullmatch(r"interwise-\d{8}T\d{6}Z\.tar\.gz\.age", p.name))
    for path in files[:-keep]:
        path.unlink()


def backup(config):
    root, state_dir = Path(config["root"]), Path(config["state_dir"])
    output_dir = state_dir / "archives"
    output_dir.mkdir(mode=0o700, exist_ok=True)
    started = time.time()
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    state_path = state_dir / "backup-state.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else {}
    state.pop("error", None)
    state.update(last_attempt=started, last_attempt_ok=False, stage="starting")
    write_json(state_path, state)
    try:
        state["stage"] = "configuration"
        env = read_env(root)
        if shutil.disk_usage(state_dir).free < 512 * 1024 * 1024:
            raise OpsError("Less than 512 MiB disk free; backup stopped")
        with tempfile.TemporaryDirectory(prefix="staging-", dir=state_dir) as temp:
            stage = Path(temp)
            state["stage"] = "mysql"
            with (stage / "mysql.sql").open("wb") as out:
                run(["docker", "exec", "interview-db", "sh", "-c",
                     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
                     '--single-transaction --quick --hex-blob --routines --triggers --events '
                     '--set-gtid-purged=OFF --no-tablespaces "$MYSQL_DATABASE"'], stdout=out)
            with (stage / "mysql.sql").open("rb") as sql:
                sql.seek(max(0, sql.seek(0, 2) - 512))
                if b"-- Dump completed on" not in sql.read():
                    raise OpsError("MySQL dump footer missing")
            state["stage"] = "redis"
            redis_temp = "/tmp/interwise-backup-" + stamp + ".rdb"
            try:
                run(["docker", "exec", "interview-redis", "redis-cli", "--rdb", redis_temp], stdout=subprocess.DEVNULL)
                run(["docker", "cp", "interview-redis:" + redis_temp, str(stage / "redis.rdb")], stdout=subprocess.DEVNULL)
            finally:
                run(["docker", "exec", "interview-redis", "rm", "-f", redis_temp], stdout=subprocess.DEVNULL)
            state["stage"] = "qdrant"
            registry_path = state_dir / "qdrant-state.json"
            backup_qdrant(env, stage, registry_path)
            state["stage"] = "files"
            names = [".env.prod", ".env.external.prod", "Caddyfile", "docker-compose.prod.yml",
                     "docker-compose.external.yml", "docker-compose.small.yml", "docker-compose.images.yml",
                     "uploads", "knowledge_storage"]
            # Host permissions are checked by tar. Unreadable required files fail the backup.
            run(["tar", "-czf", str(stage / "application-files.tar.gz"), "-C", str(root), "--", *names], stdout=subprocess.DEVNULL)
            with (stage / "caddy-files.tar.gz").open("wb") as out:
                run(["docker", "exec", "interview-caddy", "tar", "-czf", "-", "-C", "/", "data", "config"], stdout=out)
            images = json.loads(run(["docker", "inspect", "--format", "{{json .Image}}", "interview-backend"],
                                    stdout=subprocess.PIPE).stdout)
            manifest = {"format": 1, "started_at": started, "backend_image": images,
                        "consistency": "per-service snapshots; not a global transaction",
                        "files": [{"name": p.name, "bytes": p.stat().st_size, "sha256": digest(p)}
                                  for p in sorted(stage.iterdir()) if p.is_file()]}
            write_json(stage / "manifest.json", manifest)
            state["stage"] = "encrypt"
            tar_path = stage / "backup.tar.gz"
            with tarfile.open(tar_path, "w:gz", compresslevel=1) as archive:
                for name in [entry["name"] for entry in manifest["files"]] + ["manifest.json"]:
                    archive.add(stage / name, arcname=name)
            destination = output_dir / ("interwise-" + stamp + ".tar.gz.age")
            partial = destination.with_suffix(".age.partial")
            try:
                run([config["age"], "-r", config["age_recipient"], "-o", str(partial), str(tar_path)], stdout=subprocess.DEVNULL)
                partial.replace(destination)
            finally:
                partial.unlink(missing_ok=True)
            state.update(last_local_success=time.time(), archive=destination.name, sha256=digest(destination))
        retain_local(output_dir)
        state["stage"] = "qdrant_cleanup"
        # The encrypted local copy now covers the registered snapshots. Only our own
        # confirmed snapshot names may be reclaimed, never manually created snapshots.
        cleanup_qdrant(env, registry_path)
        prefix = config.get("oss_prefix", "")
        if prefix:
            state["stage"] = "oss_upload"
            target = prefix.rstrip("/") + "/" + destination.name
            oss = [config["ossutil"], "--mode", "EcsRamRole", "--region", "cn-hangzhou",
                   "--endpoint", "https://oss-cn-hangzhou-internal.aliyuncs.com"]
            run(oss + ["cp", str(destination), target, "--force"], stdout=subprocess.DEVNULL)
            # A complete read-back verifies stored bytes, not only an upload exit status.
            with tempfile.TemporaryDirectory(prefix="verify-", dir=state_dir) as temp:
                restored = Path(temp) / "uploaded.age"
                run(oss + ["cp", target, str(restored)], stdout=subprocess.DEVNULL)
                if digest(restored) != state["sha256"]:
                    raise OpsError("OSS read-back checksum mismatch")
            state.update(last_offsite_success=time.time(), oss_object=target)
        state.update(last_attempt_ok=True, stage="complete", offsite_configured=bool(prefix))
        write_json(state_path, state)
        print(json.dumps({"backup": "ok", "offsite": bool(prefix), "bytes": destination.stat().st_size}))
    except Exception as error:
        state["error"] = str(error) if isinstance(error, OpsError) else type(error).__name__
        write_json(state_path, state)
        raise OpsError("Backup failed at " + state["stage"] + ": " + state["error"]) from None


def healthy(payload):
    data = payload.get("data", {})
    return all(data.get(key) == "UP" for key in ("app", "mysql", "redis", "qdrant", "status"))


def notify(config, subject, body):
    env = read_env(Path(config["root"]), names=(".env.prod",))
    message = EmailMessage()
    message["From"] = env["MAIL_USERNAME"]
    message["To"] = config["alert_email"]
    message["Subject"] = "[InterWise] " + subject
    message.set_content(body + "\n\nHost: " + socket.gethostname())
    port = int(env.get("MAIL_PORT", "587"))
    context = ssl.create_default_context()
    if port == 465:
        client = smtplib.SMTP_SSL(env["MAIL_HOST"], port, timeout=20, context=context)
    else:
        client = smtplib.SMTP(env["MAIL_HOST"], port, timeout=20)
        client.starttls(context=context)
    with client:
        client.login(env["MAIL_USERNAME"], env["MAIL_PASSWORD"])
        client.send_message(message)


def alert_transition(previous, failures, now):
    """Alert on change, repeat at most hourly, and send one recovery."""
    active = sorted(failures)
    changed = active != previous.get("active", [])
    due = bool(active) and now - previous.get("sent_at", 0) >= 3600
    send = changed or due
    return send, {"active": active, "sent_at": now if send else previous.get("sent_at", 0)}


def check(config):
    state_dir = Path(config["state_dir"])
    path = state_dir / "monitor-state.json"
    old = json.loads(path.read_text()) if path.exists() else {}
    failures = []
    now = time.time()
    try:
        up = healthy(request_json(config["health_url"], timeout=20))
    except Exception:
        up = False
    strikes = 0 if up else old.get("health_failures", 0) + 1
    if strikes >= 3:
        failures.append("health: 3 consecutive failed checks")
    disk = shutil.disk_usage(config["root"])
    if disk.used / disk.total >= 0.8:
        failures.append("disk: usage >= 80%")
    backup_path = state_dir / "backup-state.json"
    saved = json.loads(backup_path.read_text()) if backup_path.exists() else {}
    if now - saved.get("last_local_success", 0) > 8 * 3600:
        failures.append("backup: no local success within 8 hours")
    if saved and not saved.get("last_attempt_ok"):
        # Give an in-flight backup 30 minutes before treating it as failed.
        if "error" in saved or now - saved.get("last_attempt", 0) > 1800:
            failures.append("backup: last attempt failed")
    if config.get("oss_prefix") and now - saved.get("last_offsite_success", 0) > 8 * 3600:
        failures.append("backup: no verified OSS copy within 8 hours")
    try:
        hostname = urllib.parse.urlparse(config["health_url"]).hostname
        with socket.create_connection((hostname, 443), timeout=10) as sock:
            with ssl.create_default_context().wrap_socket(sock, server_hostname=hostname) as tls:
                remaining = ssl.cert_time_to_seconds(tls.getpeercert()["notAfter"]) - now
        if remaining < 14 * 86400:
            failures.append("tls: certificate expires within 14 days")
    except Exception:
        failures.append("tls: validation failed")
    send, state = alert_transition(old, failures, now)
    state["health_failures"] = strikes
    if send:
        notify(config, "ALERT" if failures else "RECOVERED", "\n".join(failures) if failures else "All monitored checks recovered.")
    # Commit notification state only after successful delivery; SMTP failures retry next run.
    write_json(path, state)
    print(json.dumps({"health_up": up, "issues": failures, "notification_sent": send}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("backup", "check", "notify-test"))
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    state_dir = Path(config["state_dir"])
    state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        with lock(state_dir / (args.command + ".lock")):
            if args.command == "backup":
                backup(config)
            elif args.command == "check":
                check(config)
            else:
                notify(config, "TEST - no production incident", "Backup and monitoring notification delivery test.")
                print("Test notification accepted by SMTP server")
    except Exception as error:
        print(str(error) if isinstance(error, OpsError) else "Operation failed: " + type(error).__name__)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
