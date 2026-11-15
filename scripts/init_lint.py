# SPDX-License-Identifier: GPL-3.0-only
"""Install pinned lint tools inside the checkout, without changing host packages."""

import hashlib
import platform
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PMD_VERSION = "7.29.0"
PMD_SHA256 = "d27177c185b837fa57f097e482d1253468aa035379c5eb2671fa0a279109b179"
SWIFTFORMAT_VERSION = "0.64.7"
# (release asset, sha256, executable name inside the archive) by (system, machine).
SWIFTFORMAT_ASSETS = {
    ("Darwin", "arm64"): (
        "swiftformat.zip",
        "e82e224325cbd3403fc7b68caf6c917926dc106de3af875fca4b87e3c4634038",
        "swiftformat",
    ),
    ("Darwin", "x86_64"): (
        "swiftformat.zip",
        "e82e224325cbd3403fc7b68caf6c917926dc106de3af875fca4b87e3c4634038",
        "swiftformat",
    ),
    ("Linux", "x86_64"): (
        "swiftformat_linux.zip",
        "819aea026e1c630f46b234ed84481e56d9eb3cfe7024cd51b1af55169c8e13bb",
        "swiftformat_linux",
    ),
    ("Linux", "aarch64"): (
        "swiftformat_linux_aarch64.zip",
        "f42267d050f863047f6a90a1e268dd90e2145ab6c81144c304afbdc438617efd",
        "swiftformat_linux_aarch64",
    ),
}


def install_swiftformat(local):
    target = local / f"swiftformat-{SWIFTFORMAT_VERSION}" / "swiftformat"
    if target.is_file():
        return
    asset = SWIFTFORMAT_ASSETS.get((platform.system(), platform.machine()))
    if asset is None:
        print("No SwiftFormat release for this platform; Swift lint will be unavailable.")
        return
    name, digest, member = asset
    archive = local / "swiftformat.zip"
    url = (
        "https://github.com/nicklockwood/SwiftFormat/releases/download/"
        f"{SWIFTFORMAT_VERSION}/{name}"
    )
    urllib.request.urlretrieve(url, archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        archive.unlink()
        raise SystemExit("SwiftFormat checksum mismatch")
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        target.write_bytes(bundle.read(member))
    archive.unlink()
    target.chmod(0o755)


def main():
    local = ROOT / ".local/lint"
    local.mkdir(parents=True, exist_ok=True)
    venv = local / "venv"
    uv = shutil.which("uv")
    if uv:
        if not venv.exists():
            subprocess.run([uv, "venv", str(venv)], check=True)
        install = [uv, "pip", "install", "--python", str(venv / "bin/python")]
    else:
        if not venv.exists():
            subprocess.run(["python3", "-m", "venv", str(venv)], check=True)
        install = [str(venv / "bin/python"), "-m", "pip", "install"]
    subprocess.run(install + ["-r", str(ROOT / "config/lint-requirements.txt")], check=True)
    pmd = local / f"pmd-bin-{PMD_VERSION}"
    if not (pmd / "bin/pmd").is_file():
        archive = local / "pmd.zip"
        url = (
            "https://github.com/pmd/pmd/releases/download/"
            f"pmd_releases/{PMD_VERSION}/pmd-dist-{PMD_VERSION}-bin.zip"
        )
        urllib.request.urlretrieve(url, archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != PMD_SHA256:
            raise SystemExit("PMD checksum mismatch")
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(local)
        archive.unlink()
        (pmd / "bin/pmd").chmod(0o755)
    install_swiftformat(local)
    print("Lint tools ready. Java 17+ and Go are required for Java/Go checks.")


if __name__ == "__main__":
    main()
