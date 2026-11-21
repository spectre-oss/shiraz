# SPDX-License-Identifier: GPL-3.0-only
"""Compare the scoped decoder with unmodified pinned Capstone objects."""

import platform
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / ".deps/capstone/src"
ARCHIVE = ROOT / ".deps/capstone/build/libcapstone.a"


@unittest.skipUnless(
    sys.platform == "darwin"
    and platform.machine() == "arm64"
    and ARCHIVE.is_file()
    and SOURCE.is_dir(),
    "pinned arm64 macOS decoder unavailable",
)
class DecoderStreamIntegrationTests(unittest.TestCase):
    def test_random_detail_and_public_text_match_original(self):
        compiler = subprocess.check_output(["xcrun", "-f", "clang"], text=True).strip()
        cxx = subprocess.check_output(["xcrun", "-f", "clang++"], text=True).strip()
        sdk = subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip()
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            baseline = work / "baseline.a"
            shutil.copyfile(ARCHIVE, baseline)
            for name in ("cs.c", "SStream.c"):
                original = work / (name + ".o")
                subprocess.run(
                    [
                        compiler,
                        "-DCAPSTONE_ARM64_SUPPORT",
                        "-DCAPSTONE_HAS_ARM64",
                        "-DCAPSTONE_USE_SYS_DYN_MEM",
                        "-I" + str(SOURCE / "include"),
                        "-O3",
                        "-DNDEBUG",
                        "-std=gnu99",
                        "-arch",
                        "arm64",
                        "-isysroot",
                        sdk,
                        "-fPIC",
                        "-c",
                        str(SOURCE / name),
                        "-o",
                        str(original),
                    ],
                    check=True,
                )
                subprocess.run(["/usr/bin/ar", "-r", str(baseline), str(original)], check=True)
            subprocess.run(["/usr/bin/ranlib", str(baseline)], check=True)
            wrapper = work / "wrapper.c"
            wrapper.write_text(
                '#include <capstone/capstone.h>\n'
                'bool shiraz_cs_disasm_detail(csh h,const uint8_t **p,size_t *n,uint64_t *pc,cs_insn *i)'
                '{return cs_disasm_iter(h,p,n,pc,i);}\n',
                encoding="utf-8",
            )
            wrapper_object = work / "wrapper.o"
            subprocess.run(
                [
                    compiler,
                    "-I" + str(SOURCE / "include"),
                    "-arch",
                    "arm64",
                    "-isysroot",
                    sdk,
                    "-c",
                    str(wrapper),
                    "-o",
                    str(wrapper_object),
                ],
                check=True,
            )
            outputs = []
            for index, library in enumerate((baseline, ARCHIVE)):
                binary = work / f"compare-{index}"
                command = [
                    cxx,
                    "-std=c++17",
                    "-O2",
                    "-arch",
                    "arm64",
                    "-isysroot",
                    sdk,
                    "-I" + str(SOURCE / "include"),
                    str(ROOT / "tests/darwin/guest/decoder_stream_diff.cpp"),
                ]
                if index == 0:
                    command.append(str(wrapper_object))
                subprocess.run([*command, str(library), "-o", str(binary)], check=True)
                outputs.append(subprocess.check_output([str(binary)], text=True, timeout=60))
            self.assertEqual(outputs[0], outputs[1])


if __name__ == "__main__":
    unittest.main()
