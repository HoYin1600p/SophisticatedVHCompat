from __future__ import annotations

import argparse
import json
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


def main() -> int:
    parser = argparse.ArgumentParser(description="Assemble an artifact-specific release kit")
    parser.add_argument("--output")
    parser.add_argument("--github-release-url")
    args = parser.parse_args()

    config = load_config()
    version = read_version(config)
    tag = release_tag(config, version)
    artifact = select_artifact(config, version)
    details = artifact_details(artifact)
    release_url = args.github_release_url or github_release_url(config, tag)

    replacements = {
        "VERSION": version,
        "TAG": tag,
        "RELEASE_COMMIT": git_output("rev-parse", "HEAD"),
        "JAR_FILENAME": details["filename"],
        "JAR_SIZE": str(details["size"]),
        "JAR_SHA256": details["sha256"],
        "GITHUB_RELEASE_URL": release_url,
        "CURSEFORGE_PROJECT_ID": config["curseforge"]["projectId"],
        "CURSEFORGE_RELEASE_CHANNEL": config["curseforge"]["releaseChannel"],
        "CURSEFORGE_GAME_VERSION_IDS": ",".join(config["curseforge"]["gameVersionIds"]),
    }

    template_path = ROOT / config["releaseKit"]["template"]
    assembled = template_path.read_text(encoding="utf-8")
    for key, value in replacements.items():
        assembled = assembled.replace("{{" + key + "}}", value)
    if "{{" in assembled or "}}" in assembled:
        raise RuntimeError("Release-kit template contains unresolved placeholders")

    output_path = (
        Path(args.output)
        if args.output
        else ROOT / config["releaseKit"]["outputDirectory"] / f"release-kit-{version}.md"
    )
    if not output_path.is_absolute():
        output_path = ROOT / output_path
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(assembled, encoding="utf-8")

    print(json.dumps({
        "result": "PASS",
        "output": str(output_path.relative_to(ROOT)).replace("\\", "/"),
        "artifact": details,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
