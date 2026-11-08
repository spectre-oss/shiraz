#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pin unchanged compiler-rt emutls source for a genuine guest TLS fixture.

Development fixture only. Upstream Apache-2.0 WITH LLVM-exception source and
license remain under ignored .deps; no third-party implementation is vendored.
"""

import fcntl
import hashlib
import json
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".deps/compiler-rt-emutls"
REVISION = "3b5b5c1ec4a3095ab096dd780e84d7ab81f3d7ff"
FILES = {
    "lib/builtins/emutls.c": "a4437f3e329a0835adbc7df38e36df12f358d05c073b11813c409f849c2345d2",
    "LICENSE.TXT": "1a8f1058753f1ba890de984e48f0242a3a5c29a6a8f2ed9fd813f36985387e8d",
    "lib/builtins/int_lib.h": "b31130f10484636895daa13b91b63d47ea82b45547f29e4dd136b48a67854d30",
    "lib/builtins/int_types.h": "26517abcfb05e989f4ac18b38779bc118637e0a5b3e44536c3733cc47b33026e",
    "lib/builtins/int_util.h": "63d06198ab720a75c20bfd84be895c2746581b129168bae6201e7384bbb0473b",
    "lib/builtins/int_endianness.h": "ec5175561075845b7b16a92ad5ecdfd42a284de2736dd07e4ca7011b75c37709",
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT.parent / "guest-emutls.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        for source, expected in FILES.items():
            target = OUT / Path(source).name
            if target.is_symlink():
                raise ValueError("emutls fixture source must not be a symlink")
            if target.exists():
                data = target.read_bytes()
            else:
                url = f"https://raw.githubusercontent.com/llvm/llvm-project/{REVISION}/compiler-rt/{source}"
                with urllib.request.urlopen(url, timeout=30) as response:
                    data = response.read(2 * 1024 * 1024 + 1)
            if (
                len(data) > 2 * 1024 * 1024
                or hashlib.sha256(data).hexdigest() != expected
            ):
                raise ValueError(
                    f"modified/unexpected pinned compiler-rt file: {source}"
                )
            if not target.exists():
                with tempfile.NamedTemporaryFile(dir=OUT, delete=False) as pending:
                    pending.write(data)
                    temporary = Path(pending.name)
                try:
                    temporary.replace(target)
                finally:
                    temporary.unlink(missing_ok=True)
        (OUT / "provenance.json").write_text(
            json.dumps(
                {
                    "revision": REVISION,
                    "files": FILES,
                    "source": "https://github.com/llvm/llvm-project",
                    "license": "Apache-2.0 WITH LLVM-exception; LICENSE.TXT retained",
                    "scope": "development guest fixture only",
                },
                indent=2,
                sort_keys=True,
            )
            + "\n"
        )
    print(
        f"Guest emutls fixture: six unchanged compiler-rt files verified at {REVISION}"
    )


if __name__ == "__main__":
    main()
