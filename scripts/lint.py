# SPDX-License-Identifier: GPL-3.0-only
"""Read-only lint checks, with an isolated index snapshot for pre-commit."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".local/lint/venv/bin"
EXCLUDED = {"third_party", "context", ".deps", ".local", "build", "dist", "data"}
CONFIGS = (".clang-format", ".clang-tidy", "ruff.toml", "config/pmd.xml")
CPP = {".c", ".cpp", ".h", ".hpp", ".mm"}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def eligible(name):
    return not EXCLUDED.intersection(Path(name).parts) and Path(name).suffix in (
        CPP | {".py", ".java", ".go"}
    )


def selected(staged=False, base=None):
    if staged:
        raw = git("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z")
    elif base:
        raw = git("diff", "--name-only", "--diff-filter=ACMR", "-z", base, "HEAD")
    else:
        raw = git("ls-files", "-z")
    return [os.fsdecode(p) for p in raw.split(b"\0") if p and eligible(os.fsdecode(p))]


def check(root, names):
    failed = False

    def run(args, **kwargs):
        nonlocal failed
        result = subprocess.run(args, cwd=root, **kwargs)
        failed |= result.returncode != 0
        return result

    def tool(name):
        path = TOOLS / name
        if not path.is_file():
            raise RuntimeError(f"Missing {name}; run make init")
        return str(path)

    for name in names:
        suffix = Path(name).suffix
        if suffix in CPP:
            run([tool("clang-format"), "--dry-run", "--Werror", name])
        elif suffix == ".py":
            run([tool("ruff"), "check", "--no-cache", name])
            run([tool("ruff"), "format", "--check", "--no-cache", name])
        elif suffix == ".go":
            if not shutil.which("gofmt"):
                raise RuntimeError("Missing gofmt; install Go or use nix develop")
            result = run(["gofmt", "-l", name], capture_output=True, text=True)
            if result.stdout:
                print(result.stdout, end="")
                failed = True
    java = [n for n in names if Path(n).suffix == ".java"]
    if java:
        pmd = ROOT / ".local/lint/pmd-bin-7.29.0/bin/pmd"
        if not pmd.is_file() or not shutil.which("java"):
            raise RuntimeError("Missing PMD or Java 17+; run make init and install Java")
        # PMD reads a file list, so commas/spaces in filenames are preserved.
        listing = root / ".pmd-files"
        listing.write_text("\n".join(java) + "\n")
        run(
            [
                str(pmd),
                "check",
                "--file-list",
                str(listing),
                "-R",
                str(root / "config/pmd.xml"),
                "-f",
                "text",
                "--no-cache",
            ]
        )
        listing.unlink()
    return int(failed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--staged", action="store_true")
    group.add_argument("--base", help="CI comparison commit (merge base for PRs)")
    args = parser.parse_args()
    names = selected(args.staged, args.base)
    if not names:
        print("No lintable files changed.")
        return 0
    # Always isolate checks: tools cannot modify the index or working tree.
    with tempfile.TemporaryDirectory(prefix="shiraz-lint-") as directory:
        snapshot = Path(directory)
        for name in dict.fromkeys([*CONFIGS, *names]):
            dest = snapshot / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            if args.staged:
                dest.write_bytes(git("show", f":{name}"))
            else:
                dest.write_bytes((ROOT / name).read_bytes())
        return check(snapshot, names)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        raise SystemExit(str(exc)) from exc
