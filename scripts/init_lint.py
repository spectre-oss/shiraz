# SPDX-License-Identifier: GPL-3.0-only
"""Install pinned lint tools inside the checkout, without changing host packages."""

import hashlib
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PMD_VERSION = "7.29.0"
PMD_SHA256 = "d27177c185b837fa57f097e482d1253468aa035379c5eb2671fa0a279109b179"


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
    print("Lint tools ready. Java 17+ and Go are required for Java/Go checks.")


if __name__ == "__main__":
    main()
