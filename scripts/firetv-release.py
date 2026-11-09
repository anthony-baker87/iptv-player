"""Publish the CI-built, signed Fire TV APK and its updater manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import quote


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", default="firetv/app/build/outputs/apk/release/app-release.apk")
    parser.add_argument("--output", default="release/firetv")
    parser.add_argument("--manifest-only", action="store_true")
    args = parser.parse_args()
    version = os.environ["FIRETV_VERSION_NAME"]
    code = int(os.environ["FIRETV_VERSION_CODE"])
    repository = os.environ.get("GITHUB_REPOSITORY", "anthony-baker87/iptv-player")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version) or code < 1:
        raise SystemExit("Invalid release version.")
    apk = Path(args.apk)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    name = f"IPTV-FireTV-{version}.apk"
    artifact = output / name
    if apk.resolve() != artifact.resolve():
        shutil.copyfile(apk, artifact)
    tag = f"firetv-v{version}"
    manifest = {
        "packageName": "com.iptvdesktop.firetv",
        "versionName": version,
        "versionCode": code,
        "apkUrl": f"https://github.com/{repository}/releases/download/{tag}/{quote(name)}",
        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
        "size": artifact.stat().st_size,
        "commit": os.environ.get("GITHUB_SHA", "local"),
    }
    manifest_file = output / "firetv-update.json"
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    if args.manifest_only:
        print(f"Generated Fire TV {version} updater manifest.")
        return
    # Never replace an already-published APK: installed version codes must be unique.
    existing = subprocess.run(["gh", "release", "view", tag, "--repo", repository], capture_output=True)
    if existing.returncode == 0:
        print(f"Release {tag} already exists; no published assets changed.")
        return
    notes = output / "release-notes.md"
    notes.write_text(
        f"Fire TV build {version} from commit {manifest['commit']}.\n\n"
        "Download the APK for a first install, or open Updates in IPTV Fire TV. "
        "Approve the installation on your TV. Playlist, favorites, and history are preserved.\n\n"
        "This APK uses the same signing identity as the original personal Fire TV preview. "
        "It is a personal sideload build, not an Amazon Appstore release.\n",
        encoding="utf-8",
    )
    subprocess.run([
        "gh", "release", "create", tag, str(artifact), str(manifest_file),
        "--repo", repository, "--target", os.environ["GITHUB_SHA"],
        "--title", f"IPTV Fire TV {version}", "--notes-file", str(notes), "--latest",
    ], check=True)


if __name__ == "__main__":
    main()
