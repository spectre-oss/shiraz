# SPDX-License-Identifier: GPL-3.0-only
import fcntl
import os
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from shiraz.apk import FormatError
from shiraz.darwin_maps import cache_catalog_fd


class CacheCapabilityTests(unittest.TestCase):
    def test_read_only_unlinked_capability_and_cleanup(self):
        blob = b"SHZMAP2\0" + bytes(16) + struct.pack("<I", 1) + bytes(44)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            child_run = subprocess.run
            with patch(
                "shiraz.darwin_maps.subprocess.run",
                return_value=subprocess.CompletedProcess([], 0, blob, b""),
            ) as run:
                with self.assertRaisesRegex(RuntimeError, "child failure"):
                    with cache_catalog_fd(root, root) as fd:
                        self.assertEqual(os.read(fd, len(blob)), blob)
                        self.assertEqual(os.fstat(fd).st_nlink, 0)
                        self.assertEqual(fcntl.fcntl(fd, fcntl.F_GETFL) & os.O_ACCMODE, os.O_RDONLY)
                        self.assertFalse(os.get_inheritable(fd))
                        self.assertEqual(list(root.iterdir()), [])
                        child = child_run(
                            [
                                sys.executable,
                                "-I",
                                "-c",
                                "import os,sys;os.write(1,os.pread(int(sys.argv[1]),4096,0))",
                                str(fd),
                            ],
                            pass_fds=(fd,),
                            capture_output=True,
                            check=True,
                        )
                        self.assertEqual(child.stdout, blob)
                        raise RuntimeError("child failure")
                with self.assertRaises(OSError):
                    os.fstat(fd)
                self.assertIn("--with-paths", run.call_args.args[0])

    def test_invalid_helper_output_never_becomes_capability(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for blob, code in [(b"", 0), (b"x" * 28, 0), (b"SHZMAP2\0" + bytes(20), 0), (b"", 1)]:
                with patch(
                    "shiraz.darwin_maps.subprocess.run",
                    return_value=subprocess.CompletedProcess([], code, blob, b""),
                ):
                    with self.assertRaises(FormatError), cache_catalog_fd(root, root):
                        self.fail("invalid metadata accepted")
                self.assertEqual(list(root.iterdir()), [])
