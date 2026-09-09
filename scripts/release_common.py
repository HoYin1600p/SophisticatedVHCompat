from __future__ import annotations

import fnmatch
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / ".codex" / "mod-publish.json"


def load_config() -> dict:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def read_version(config: dict) -> str:
    version_config = config["version"]
    source = ROOT / version_config["sourceFiles"][0]
    property_name = re.escape(version_config["property"])
    match = re.search(
        rf"(?m)^{property_name}\s*=\s*([^\s#]+)\s*$",
        source.read_text(encoding="utf-8"),
    )
    if not match:
        raise RuntimeError(f"Unable to read {version_config['property']} from {source}")
    return match.group(1)


def select_artifact(config: dict, version: str) -> Path:
    build = config["build"]
    candidates = sorted(ROOT.glob(build["artifactGlob"]))
    candidates = [
        path
        for path in candidates
        if path.is_file()
        and not any(fnmatch.fnmatch(path.name, pattern) for pattern in build["excludeGlobs"])
    ]
    if len(candidates) != 1:
        names = [str(path.relative_to(ROOT)) for path in candidates]
        raise RuntimeError(f"Expected exactly one release artifact, found {names}")

    artifact = candidates[0]
    if version not in artifact.name:
        raise RuntimeError(f"Artifact {artifact.name} does not contain version {version}")
    inspect_artifact_version(artifact, version)
    return artifact


def inspect_artifact_version(artifact: Path, version: str) -> None:
    with zipfile.ZipFile(artifact) as archive:
        mods_toml = archive.read("META-INF/mods.toml").decode("utf-8")
        mixin_config = archive.read("sophisticated_vh_compat.mixins.json")
        if not mixin_config:
            raise RuntimeError("Packaged mixin configuration is empty")

    match = re.search(r'(?m)^version\s*=\s*"([^"]+)"\s*$', mods_toml)
    if not match or match.group(1) != version:
        packaged = match.group(1) if match else None
        raise RuntimeError(f"Packaged version {packaged!r} does not match {version}")
    if 'modId = "sophisticated_vh_compat"' not in mods_toml:
        raise RuntimeError("Packaged mod identity is incorrect")


def artifact_details(artifact: Path) -> dict:
    data = artifact.read_bytes()
    return {
        "path": str(artifact.relative_to(ROOT)).replace("\\", "/"),
        "filename": artifact.name,
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def git_output(*args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def release_tag(config: dict, version: str) -> str:
    return f"{config['version']['tagPrefix']}{version}"


def github_release_url(config: dict, tag: str) -> str:
    return f"https://github.com/{config['github']['repository']}/releases/tag/{tag}"
