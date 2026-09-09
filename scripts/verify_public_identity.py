from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
import zipfile
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from urllib.parse import urlsplit


PROHIBITED = base64.b64decode("RXRoYW4=")
TOKEN_PATTERN = re.compile(
    rb"(?i)(?<![a-z0-9])" + re.escape(PROHIBITED) + rb"(?![a-z0-9])"
)


def git(root: Path, *args: str, binary: bool = False, check: bool = True):
    completed = subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=not binary,
    )
    return completed.stdout


def remote_identity(remote: str) -> str:
    remote = remote.strip()
    if not remote:
        return ""
    if "://" in remote:
        parsed = urlsplit(remote)
        host = (parsed.hostname or "").lower()
        path = parsed.path.strip("/")
    elif "@" in remote and ":" in remote:
        host_path = remote.split("@", 1)[1]
        host, path = host_path.split(":", 1)
        host = host.lower()
        path = path.strip("/")
    else:
        return remote.removesuffix(".git").replace("\\", "/")
    return f"{host}/{path.removesuffix('.git')}"


def repository_identity(root: Path) -> tuple[str, str]:
    remote = git(root, "remote", "get-url", "origin", check=False).strip()
    normalized_remote = remote_identity(remote)
    name = normalized_remote.rsplit("/", 1)[-1] if normalized_remote else root.name
    return name, normalized_remote


def normalized_root_hash(root: Path) -> str:
    normalized = str(root.resolve()).replace("\\", "/").casefold()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def locate_shadow_workspace(root: Path, repository_name: str) -> Path:
    override = os.environ.get("CODEX_SHADOW_WORKSPACE")
    if override:
        return Path(override).resolve()
    return (root.parent / "Codex Workspaces" / repository_name).resolve()


def validate_marker(root: Path, shadow: Path, name: str, remote: str) -> dict:
    marker_path = shadow / "workspace-identity.json"
    marker = json.loads(marker_path.read_text(encoding="utf-8-sig"))
    expected_hash = normalized_root_hash(root)
    expected_remote = remote
    if marker.get("canonical_repository_name") != name:
        raise RuntimeError("Shadow workspace repository marker does not match")
    if marker.get("remote_identity", "") != expected_remote:
        raise RuntimeError("Shadow workspace remote marker does not match")
    if marker.get("git_root_path_sha256") != expected_hash:
        raise RuntimeError("Shadow workspace root hash does not match")
    return marker


def commit_exists(root: Path, commit: str) -> bool:
    result = subprocess.run(
        ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def read_checkpoints(log_path: Path, name: str, remote: str) -> list[dict]:
    if not log_path.exists():
        return []
    text = log_path.read_text(encoding="utf-8-sig", errors="replace")
    checkpoints = []
    for match in re.finditer(r"\x60\x60\x60json\s*(\{.*?\})\s*\x60\x60\x60", text, re.DOTALL):
        try:
            entry = json.loads(match.group(1))
        except json.JSONDecodeError:
            continue
        entry_name = entry.get("repository_identity")
        entry_remote = remote_identity(entry.get("public_remote", ""))
        frontier = entry.get("scanned_frontier") or []
        if (
            entry.get("result") == "PASS"
            and entry_name == name
            and entry_remote == remote
            and frontier
        ):
            checkpoints.append(entry)
    return checkpoints


def newest_valid_checkpoint(root: Path, checkpoints: list[dict]) -> dict | None:
    for entry in reversed(checkpoints):
        frontier = [str(value) for value in entry.get("scanned_frontier", [])]
        if frontier and all(commit_exists(root, commit) for commit in frontier):
            return entry
    return None


def contains_prohibited(data: bytes) -> bool:
    return TOKEN_PATTERN.search(data) is not None


def fingerprint(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def scan_zip(data: bytes, label: str, findings: list[str], counter: list[int]) -> None:
    with zipfile.ZipFile(BytesIO(data)) as archive:
        for entry in archive.infolist():
            counter[0] += 1
            entry_label = f"{label}!/{entry.filename}"
            if contains_prohibited(entry.filename.encode("utf-8", errors="ignore")):
                findings.append(f"archive entry name: {entry_label}")
            if entry.is_dir():
                continue
            contents = archive.read(entry)
            if contains_prohibited(contents):
                findings.append(f"archive entry content: {entry_label}")
            if entry.filename.lower().endswith((".jar", ".zip")):
                try:
                    scan_zip(contents, entry_label, findings, counter)
                except zipfile.BadZipFile:
                    pass


def changed_paths(root: Path, commit: str) -> list[str]:
    output = git(
        root,
        "diff-tree",
        "--root",
        "--no-commit-id",
        "--name-only",
        "-r",
        "-z",
        commit,
        binary=True,
    )
    return [
        value.decode("utf-8", errors="surrogateescape")
        for value in output.split(b"\0")
        if value
    ]


def scan_commit(root: Path, commit: str, findings: list[str]) -> None:
    metadata = git(
        root,
        "show",
        "-s",
        "--format=%H%n%an%n%ae%n%cn%n%ce%n%B",
        commit,
        binary=True,
    )
    if contains_prohibited(metadata):
        findings.append(f"commit metadata: {commit}")
    for path in changed_paths(root, commit):
        if contains_prohibited(path.encode("utf-8", errors="surrogateescape")):
            findings.append(f"commit path: {commit}:{path}")
        result = subprocess.run(
            ["git", "cat-file", "-e", f"{commit}:{path}"],
            cwd=root,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0:
            contents = git(root, "show", f"{commit}:{path}", binary=True)
            if contains_prohibited(contents):
                findings.append(f"commit content: {commit}:{path}")


def scan_refs(root: Path, findings: list[str]) -> list[str]:
    refs = git(
        root,
        "for-each-ref",
        "--format=%(refname)%00%(objectname)%00%(authorname)%00%(authoremail)%00%(committername)%00%(committeremail)%00%(subject)%00%(contents)",
        binary=True,
    )
    if contains_prohibited(refs):
        findings.append("current ref name or metadata")
    lines = []
    for raw in refs.splitlines():
        fields = raw.split(b"\0")
        if len(fields) >= 2:
            lines.append(
                fields[0].decode("utf-8", errors="replace")
                + " "
                + fields[1].decode("ascii", errors="replace")
            )
    return lines


def tracked_and_untracked(root: Path, findings: list[str]) -> tuple[dict, dict]:
    tracked = {}
    tracked_output = git(root, "ls-files", "-z", binary=True)
    for raw in tracked_output.split(b"\0"):
        if not raw:
            continue
        path_text = raw.decode("utf-8", errors="surrogateescape")
        path = root / path_text
        if contains_prohibited(raw):
            findings.append(f"working-tree path: {path_text}")
        if path.is_file():
            data = path.read_bytes()
            tracked[path_text.replace("\\", "/")] = hashlib.sha256(data).hexdigest()
            if contains_prohibited(data):
                findings.append(f"working-tree content: {path_text}")

    untracked = {}
    untracked_output = git(
        root, "ls-files", "--others", "--exclude-standard", "-z", binary=True
    )
    for raw in untracked_output.split(b"\0"):
        if not raw:
            continue
        path_text = raw.decode("utf-8", errors="surrogateescape")
        path = root / path_text
        if contains_prohibited(raw):
            findings.append(f"untracked path: {path_text}")
        if path.is_file():
            data = path.read_bytes()
            untracked[path_text.replace("\\", "/")] = hashlib.sha256(data).hexdigest()
            if contains_prohibited(data):
                findings.append(f"untracked content: {path_text}")
    return tracked, untracked


def scan_artifacts(root: Path, findings: list[str]) -> list[dict]:
    artifacts = []
    for path in sorted((root / "build" / "libs").glob("*.jar")):
        data = path.read_bytes()
        archive_findings: list[str] = []
        counter = [0]
        scan_zip(data, str(path.relative_to(root)).replace("\\", "/"), archive_findings, counter)
        findings.extend(archive_findings)
        artifacts.append(
            {
                "path": str(path.relative_to(root)).replace("\\", "/"),
                "size": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
                "archive_entries_scanned": counter[0],
            }
        )
    return artifacts


def append_entry(log_path: Path, timestamp: str, entry: dict) -> None:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path = log_path.with_suffix(log_path.suffix + ".lock")
    lock_fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    try:
        os.close(lock_fd)
        payload = (
            f"\n\n## {timestamp}\n\n"
            + "\x60\x60\x60json\n"
            + json.dumps(entry, indent=2, sort_keys=False)
            + "\n\x60\x60\x60\n"
        ).encode("utf-8")
        fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_APPEND)
        try:
            written = os.write(fd, payload)
            os.fsync(fd)
        finally:
            os.close(fd)
        if written != len(payload):
            raise RuntimeError("Identity log append was incomplete")
    finally:
        try:
            lock_path.unlink()
        except FileNotFoundError:
            pass


def scan_repository(
    root: Path,
    shadow_override: Path | None = None,
    forced_result: str | None = None,
) -> dict:
    root = root.resolve()
    name, remote = repository_identity(root)
    shadow = shadow_override or locate_shadow_workspace(root, name)
    marker = validate_marker(root, shadow, name, remote)
    log_path = shadow / "identity-scan" / "identity-scan-log.md"
    checkpoints = read_checkpoints(log_path, name, remote)
    checkpoint = newest_valid_checkpoint(root, checkpoints)
    prior_frontier = (
        [str(value) for value in checkpoint["scanned_frontier"]] if checkpoint else []
    )
    timestamp = datetime.now(timezone.utc).isoformat()
    findings: list[str] = []
    refs: list[str] = []
    commits: list[str] = []
    tracked = {}
    untracked = {}
    artifacts = []
    result = "INCOMPLETE"
    error = None

    try:
        if forced_result == "INCOMPLETE":
            raise RuntimeError("simulated interruption")
        rev_args = ["rev-list", "--all"]
        expression = "git rev-list --all"
        if prior_frontier:
            rev_args.extend(["--not", *prior_frontier])
            expression += " --not " + " ".join(prior_frontier)
        commits = [line for line in git(root, *rev_args).splitlines() if line]
        refs = scan_refs(root, findings)
        for commit in commits:
            scan_commit(root, commit, findings)
        tracked, untracked = tracked_and_untracked(root, findings)
        artifacts = scan_artifacts(root, findings)
        result = "FAIL" if findings or forced_result == "FAIL" else "PASS"
    except Exception as exc:
        expression = "scan interrupted before completion"
        error = type(exc).__name__
        result = "INCOMPLETE"

    frontier = []
    if result == "PASS":
        head = git(root, "rev-parse", "HEAD").strip()
        frontier = [head]

    entry = {
        "timestamp_utc": timestamp,
        "repository_identity": name,
        "git_root_path_sha256": marker["git_root_path_sha256"],
        "public_remote": remote,
        "prior_checkpoint": checkpoint.get("timestamp_utc") if checkpoint else None,
        "prior_frontier": prior_frontier,
        "result": result,
        "refs_examined": refs,
        "commit_range": {"expression": expression, "commits": commits},
        "working_tree_scope": {
            "tracked_sha256": tracked,
            "untracked_nonignored_sha256": untracked,
        },
        "artifacts": artifacts,
        "findings": findings,
        "error_type": error,
        "scanned_frontier": frontier,
    }
    append_entry(log_path, timestamp, entry)
    return {
        "result": result,
        "log": str(log_path),
        "atomicLogAppend": True,
        "findings": len(findings),
        "scannedFrontier": frontier,
    }


def create_test_repository(base: Path) -> tuple[Path, Path]:
    root = base / "repo"
    shadow = base / "Codex Workspaces" / "test"
    root.mkdir(parents=True)
    git(root, "init", "-b", "main")
    git(root, "config", "user.name", "HoYin1600p")
    git(root, "config", "user.email", "hoyin1600p@gmail.com")
    git(root, "remote", "add", "origin", "https://github.com/HoYin1600p/test.git")
    (root / "safe.txt").write_text("safe\n", encoding="utf-8")
    git(root, "add", "safe.txt")
    git(root, "commit", "-m", "safe")
    shadow.mkdir(parents=True)
    marker = {
        "schema_version": 1,
        "canonical_repository_name": "test",
        "remote_identity": "github.com/HoYin1600p/test",
        "git_root_path_sha256": normalized_root_hash(root),
    }
    (shadow / "workspace-identity.json").write_text(
        json.dumps(marker), encoding="utf-8"
    )
    return root, shadow


def self_test() -> dict:
    with tempfile.TemporaryDirectory() as temp:
        root, shadow = create_test_repository(Path(temp))
        log_path = shadow / "identity-scan" / "identity-scan-log.md"
        log_path.parent.mkdir(parents=True)
        log_path.write_text("# preserved history\n", encoding="utf-8")

        passed = scan_repository(root, shadow)
        preserved = log_path.read_text(encoding="utf-8").startswith("# preserved history")

        prohibited_text = PROHIBITED.decode("ascii")
        (root / "blocked.txt").write_text(prohibited_text, encoding="utf-8")
        git(root, "add", "blocked.txt")
        git(root, "commit", "-m", "blocked fixture")
        failed = scan_repository(root, shadow)

        (root / "blocked.txt").unlink()
        git(root, "add", "-u")
        git(root, "commit", "-m", "remove fixture")
        incomplete = scan_repository(root, shadow, forced_result="INCOMPLETE")

        text = log_path.read_text(encoding="utf-8")
        checks = {
            "pass": passed["result"] == "PASS",
            "fail": failed["result"] == "FAIL",
            "incomplete": incomplete["result"] == "INCOMPLETE",
            "historyPreserved": preserved,
            "allResultsLogged": all(
                f'\"result\": \"{value}\"' in text
                for value in ("PASS", "FAIL", "INCOMPLETE")
            ),
            "lockRemoved": not log_path.with_suffix(log_path.suffix + ".lock").exists(),
        }
        return {"result": "PASS" if all(checks.values()) else "FAIL", "checks": checks}


def main() -> int:
    parser = argparse.ArgumentParser(description="Scan public repository identity")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        result = self_test()
    else:
        result = scan_repository(Path.cwd())
    print(json.dumps(result, sort_keys=True))
    return 0 if result["result"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
