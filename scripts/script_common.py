"""Helpers shared by the stand-alone scripts in this directory.

Scripts import this module by bare name, which works because the interpreter
puts the script's own directory first on sys.path for ``python3 scripts/x.py``
as well as ``uv run --no-project python scripts/x.py``.
"""

import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(argv, cwd=ROOT, env=None):
    """Run a command, failing on a non-zero exit; every argument is stringified."""
    subprocess.run([str(x) for x in argv], cwd=cwd, env=env, check=True)


def sha256_file(path):
    """Hex SHA-256 of a file, read in 1 MiB blocks."""
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()
