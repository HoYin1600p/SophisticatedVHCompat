from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

from release_common import (
    ROOT,
    artifact_details,
    github_release_url,
    git_output,
    load_config,
    read_version,
    release_tag,
    select_artifact,
)


def read_user_environment(name: str) -> str | None:
    if os.name != "nt":
        return None
    try:
        import winreg

        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as key:
            value, _ = winreg.QueryValueEx(key, name)
            return value or None
    except (FileNotFoundError, OSError):
        return None


def configured_token() -> str | None:
    return os.environ.get("CURSEFORGE_API_TOKEN") or read_user_environment(
        "CURSEFORGE_API_TOKEN"
    )


def read_changelog(path_value: str | None, release_url: str, rehearsal: bool) -> tuple[str, Path]:
    if path_value:
        path = Path(path_value)
        if not path.is_absolute():
            path = ROOT / path
    elif rehearsal:
        path = ROOT / "build" / "release-rehearsal" / "curseforge-changelog.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "Onboarding rehearsal only. No file will be uploaded.\n\n"
            f"Full changelog: {release_url}\n",
            encoding="utf-8",
        )
    else:
        raise RuntimeError(
            "Provide --changelog-file or CURSEFORGE_CHANGELOG_FILE for a real upload"
        )

    changelog = path.read_text(encoding="utf-8")
    if release_url not in changelog:
        raise RuntimeError("CurseForge changelog does not contain the GitHub release URL")
    return changelog, path


def build_metadata(config: dict, version: str, changelog: str) -> dict:
    curseforge = config["curseforge"]
    relations = {
        "projects": [
            {
                "slug": relation["slug"],
                "projectID": relation["projectId"],
                "type": relation["type"],
            }
            for relation in curseforge.get("relations", [])
        ]
    }
    return {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": curseforge["displayNameTemplate"].format(version=version),
        "gameVersions": [int(value) for value in curseforge["gameVersionIds"]],
        "releaseType": curseforge["releaseChannel"],
        "isMarkedForManualRelease": bool(curseforge["manualRelease"]),
        "relations": relations,
    }


def encode_multipart(metadata: dict, artifact: Path) -> tuple[bytes, str]:
    boundary = "----SophisticatedVHCompat" + uuid.uuid4().hex
    boundary_bytes = boundary.encode("ascii")
    chunks = [
        b"--" + boundary_bytes + b"\r\n",
        b'Content-Disposition: form-data; name="metadata"\r\n',
        b"Content-Type: application/json\r\n\r\n",
        json.dumps(metadata, separators=(",", ":")).encode("utf-8"),
        b"\r\n--" + boundary_bytes + b"\r\n",
        (
            'Content-Disposition: form-data; name="file"; filename="'
            + artifact.name
            + '"\r\n'
        ).encode("utf-8"),
        b"Content-Type: application/java-archive\r\n\r\n",
        artifact.read_bytes(),
        b"\r\n--" + boundary_bytes + b"--\r\n",
    ]
    return b"".join(chunks), boundary


def upload_error_message(error: urllib.error.HTTPError) -> str:
    try:
        payload = json.loads(error.read().decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return ""

    message = payload.get("error") or payload.get("message")
    if not isinstance(message, str):
        return ""
    return re.sub(r"\s+", " ", message).strip()[:500]


def upload(config: dict, metadata: dict, artifact: Path) -> dict:
    guard = config["curseforge"]["projectClassGuard"]
    if not guard["allowUpload"]:
        raise RuntimeError(
            "CurseForge upload is disabled until the project class is verified or corrected"
        )
    token = configured_token()
    if not token:
        raise RuntimeError("CurseForge author credential is unavailable")

    body, boundary = encode_multipart(metadata, artifact)
    project_id = config["curseforge"]["projectId"]
    request = urllib.request.Request(
        f"https://minecraft.curseforge.com/api/projects/{project_id}/upload-file",
        data=body,
        method="POST",
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "X-Api-Token": token,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = json.loads(response.read().decode("utf-8"))
            status = response.status
    except urllib.error.HTTPError as error:
        message = upload_error_message(error)
        suffix = f": {message}" if message else ""
        raise RuntimeError(f"CurseForge upload failed with HTTP {error.code}{suffix}") from None
    except urllib.error.URLError:
        raise RuntimeError("CurseForge upload request failed") from None
    finally:
        token = None

    file_id = payload.get("id")
    if not file_id:
        raise RuntimeError("CurseForge upload response did not contain a file ID")
    return {"httpStatus": status, "fileId": str(file_id)}


def validate_real_upload_state(config: dict, version: str, tag: str) -> None:
    if git_output("status", "--porcelain"):
        raise RuntimeError("Working tree must be clean for upload")
    tag_commit = git_output("rev-parse", f"refs/tags/{tag}^{{}}")
    if not tag_commit:
        raise RuntimeError(f"Tag {tag} must resolve to a commit before upload")

    ledger_path = ROOT / config["releaseLedger"]
    ledger = json.loads(ledger_path.read_text(encoding="utf-8"))
    matches = [entry for entry in ledger["releases"] if entry["version"] == version]
    if len(matches) != 1 or matches[0]["state"] != "prepared":
        raise RuntimeError("Ledger must contain exactly one prepared entry for this version")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build or upload CurseForge release metadata")
    parser.add_argument("command", choices=("dry-run", "upload"))
    parser.add_argument("--rehearsal", action="store_true")
    parser.add_argument("--changelog-file", default=os.environ.get("CURSEFORGE_CHANGELOG_FILE"))
    parser.add_argument("--github-release-url", default=os.environ.get("GITHUB_RELEASE_URL"))
    args = parser.parse_args()

    try:
        config = load_config()
        version = read_version(config)
        tag = release_tag(config, version)
        release_url = args.github_release_url or github_release_url(config, tag)
        artifact = select_artifact(config, version)
        details = artifact_details(artifact)
        changelog, changelog_path = read_changelog(
            args.changelog_file, release_url, args.rehearsal
        )
        metadata = build_metadata(config, version, changelog)

        if args.command == "dry-run":
            output_path = ROOT / "build" / "release-rehearsal" / "curseforge-payload.json"
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(
                json.dumps(metadata, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            print(json.dumps({
                "result": "PASS",
                "mode": "dry-run",
                "remoteWrite": False,
                "artifact": details,
                "metadataPath": str(output_path.relative_to(ROOT)).replace("\\", "/"),
                "changelogPath": str(changelog_path.relative_to(ROOT)).replace("\\", "/"),
                "projectClassGuard": config["curseforge"]["projectClassGuard"],
            }, sort_keys=True))
            return 0

        validate_real_upload_state(config, version, tag)
        result = upload(config, metadata, artifact)
        print(json.dumps({
            "result": "UPLOADED",
            "artifact": details,
            **result,
        }, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError) as error:
        print(json.dumps({"result": "FAIL", "message": str(error)}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
