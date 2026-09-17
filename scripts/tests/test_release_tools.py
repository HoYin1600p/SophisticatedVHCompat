from __future__ import annotations

import io
import json
import subprocess
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))

import assemble_release_kit
import curseforge_publish
from release_common import artifact_details, load_config, read_version, select_artifact


class ReleaseConfigurationTest(unittest.TestCase):
    def test_manifest_and_ledger_have_revision_five_contract(self):
        config = load_config()
        self.assertEqual("publish-mod-update", config["workflow"]["skill"])
        self.assertEqual(5, config["workflow"]["revision"])
        self.assertFalse(config["github"]["usesAutomaticUpdateCheck"])
        self.assertTrue(config["curseforge"]["projectClassGuard"]["allowUpload"])
        self.assertNotIn("token", json.dumps(config).casefold())

        ledger = json.loads((ROOT / config["releaseLedger"]).read_text(encoding="utf-8"))
        self.assertEqual(["0.0.2", "0.0.1"], [entry["version"] for entry in ledger["releases"]])
        prepared = next(entry for entry in ledger["releases"] if entry["version"] == "0.0.2")
        self.assertEqual("prepared", prepared["state"])

    def test_artifact_selection_and_packaged_version(self):
        config = load_config()
        version = read_version(config)
        artifact = select_artifact(config, version)
        details = artifact_details(artifact)
        self.assertEqual("0.0.2", version)
        self.assertTrue(details["filename"].endswith("-0.0.2-all.jar"))
        self.assertEqual(64, len(details["sha256"]))

    def test_dry_run_never_calls_upload(self):
        original_argv = sys.argv
        output = io.StringIO()
        try:
            sys.argv = ["curseforge_publish.py", "dry-run", "--rehearsal"]
            with mock.patch.object(
                curseforge_publish,
                "upload",
                side_effect=AssertionError("network upload must not run"),
            ), redirect_stdout(output):
                self.assertEqual(0, curseforge_publish.main())
        finally:
            sys.argv = original_argv

        result = json.loads(output.getvalue())
        self.assertEqual("PASS", result["result"])
        self.assertFalse(result["remoteWrite"])
        self.assertEqual(
            artifact_details(
                select_artifact(load_config(), read_version(load_config()))
            )["sha256"],
            result["artifact"]["sha256"],
        )

    def test_upload_guard_permits_network_after_project_class_is_corrected(self):
        config = load_config()
        artifact = select_artifact(config, read_version(config))
        response = mock.MagicMock()
        response.status = 200
        response.read.return_value = b'{"id": 1}'
        response.__enter__.return_value = response
        with mock.patch("urllib.request.urlopen", return_value=response) as urlopen:
            with mock.patch.object(curseforge_publish, "configured_token", return_value="test-token"):
                self.assertEqual(curseforge_publish.upload(config, {}, artifact), {"httpStatus": 200, "fileId": "1"})
        self.assertTrue(urlopen.called)

    def test_upload_metadata_uses_validated_ids_and_optional_relations(self):
        config = load_config()
        metadata = curseforge_publish.build_metadata(config, "0.0.2", "changes")
        self.assertEqual(
            [9008, 9016, 7498, 8326, 9638, 9639],
            metadata["gameVersions"],
        )
        self.assertEqual("release", metadata["releaseType"])
        self.assertFalse(metadata["isMarkedForManualRelease"])
        self.assertEqual(
            {"optionalDependency"},
            {entry["type"] for entry in metadata["relations"]["projects"]},
        )

    def test_release_kit_uses_post_build_artifact_values(self):
        template = ROOT / "docs" / "release-kit-template.md"
        before = template.read_text(encoding="utf-8")
        output = ROOT / "build" / "release-rehearsal" / "test-release-kit.md"
        original_argv = sys.argv
        captured = io.StringIO()
        try:
            sys.argv = ["assemble_release_kit.py", "--output", str(output)]
            with redirect_stdout(captured):
                self.assertEqual(0, assemble_release_kit.main())
        finally:
            sys.argv = original_argv

        assembled = output.read_text(encoding="utf-8")
        self.assertNotIn("{{", assembled)
        self.assertEqual(before, template.read_text(encoding="utf-8"))
        details = artifact_details(
            select_artifact(load_config(), read_version(load_config()))
        )
        self.assertIn(details["sha256"], assembled)
        self.assertIn(str(details["size"]), assembled)


class CatalogValidatorTest(unittest.TestCase):
    helper = SCRIPTS / "Test-CurseForgeGameVersions.ps1"
    fixture = [
        {"id": 9008, "name": "1.18.2", "gameVersionTypeID": 73250},
        {"id": 9016, "name": "1.18.2", "gameVersionTypeID": 1},
        {"id": 7498, "name": "Forge", "gameVersionTypeID": 68441},
        {"id": 8326, "name": "Java 17", "gameVersionTypeID": 2},
        {"id": 9638, "name": "Client", "gameVersionTypeID": 75208},
        {"id": 9639, "name": "Server", "gameVersionTypeID": 75208},
    ]

    def invoke(self, ids: list[str], required: list[str]):
        completed = subprocess.run(
            [
                "pwsh",
                "-NoProfile",
                "-File",
                str(self.helper),
                "-GameVersionIds",
                ",".join(ids),
                "-RequiredGameVersionTypeIds",
                ",".join(required),
                "-CatalogJson",
                json.dumps(self.fixture, separators=(",", ":")),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        output = completed.stdout.strip()
        self.assertNotIn("X-Api-Token", output + completed.stderr)
        self.assertNotIn("Authorization", output + completed.stderr)
        return completed.returncode, json.loads(output)

    def test_valid_concrete_ids_cover_every_required_group(self):
        code, result = self.invoke(
            ["9008", "9016", "7498", "8326", "9638", "9639"],
            ["1", "73250", "68441", "2", "75208"],
        )
        self.assertEqual(0, code)
        self.assertEqual("PASS", result["result"])

    def test_type_id_used_as_upload_id_fails(self):
        code, result = self.invoke(["1"], ["1"])
        self.assertNotEqual(0, code)
        self.assertEqual("FAIL", result["result"])
        self.assertEqual(["1"], result["missingIds"])

    def test_missing_required_group_fails(self):
        code, result = self.invoke(["9008", "9016", "7498"], ["1", "73250", "68441", "2"])
        self.assertNotEqual(0, code)
        self.assertEqual(["2"], result["missingRequiredTypeIds"])


if __name__ == "__main__":
    unittest.main()
