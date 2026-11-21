# SPDX-License-Identifier: GPL-3.0-only
"""Compare the generated lookup with the actual pinned upstream lookup."""

import platform
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import guest_decoder_lookup as lookup  # noqa: E402

SOURCE = ROOT / ".deps/capstone/src"
ARCHIVE = ROOT / ".deps/capstone/build/libcapstone.a"


@unittest.skipUnless(
    sys.platform == "darwin"
    and platform.machine() == "arm64"
    and ARCHIVE.is_file()
    and SOURCE.is_dir(),
    "pinned arm64 macOS decoder unavailable",
)
class DecoderLookupIntegrationTests(unittest.TestCase):
    def test_original_and_generated_mapping_agree(self):
        compiler = subprocess.check_output(["xcrun", "-f", "clang"], text=True).strip()
        cxx = subprocess.check_output(["xcrun", "-f", "clang++"], text=True).strip()
        sdk = subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip()
        mapping = SOURCE / "arch/AArch64"
        source = (mapping / "AArch64Mapping.c").read_text(encoding="utf-8")
        included = (mapping / "AArch64MappingInsnName.inc").read_text(encoding="utf-8")
        lookup.optimized_mapping(source, included)  # Check the expected pinned input.
        tail = source.split(lookup._TABLE, 1)[1].split("};", 1)[0].split(lookup._INCLUDE, 1)[1]
        names = lookup._entries(included) + lookup._entries(tail)
        self.assertEqual(len(names), lookup.PINNED_NAME_COUNT)
        inputs = names + [name + "_unknown" for name in names] + ["", "ADD", "not-an-instruction"]
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            baseline, optimized = work / "base.a", work / "optimized.a"
            shutil.copyfile(ARCHIVE, baseline)
            object_dir = work / "original"
            object_dir.mkdir()
            original_object = object_dir / "AArch64Mapping.c.o"
            subprocess.run(
                [
                    compiler,
                    "-DCAPSTONE_ARM64_SUPPORT",
                    "-DCAPSTONE_HAS_ARM64",
                    "-DCAPSTONE_USE_SYS_DYN_MEM",
                    "-I" + str(SOURCE / "include"),
                    "-I" + str(mapping),
                    "-O3",
                    "-DNDEBUG",
                    "-std=gnu99",
                    "-arch",
                    "arm64",
                    "-isysroot",
                    sdk,
                    "-fPIC",
                    "-c",
                    str(mapping / "AArch64Mapping.c"),
                    "-o",
                    str(original_object),
                ],
                check=True,
            )
            subprocess.run(["/usr/bin/ar", "-r", str(baseline), str(original_object)], check=True)
            subprocess.run(["/usr/bin/ranlib", str(baseline)], check=True)
            shutil.copyfile(baseline, optimized)
            lookup.build_mapping_object(SOURCE, optimized, work / "generated", compiler, sdk)
            driver = work / "compare.cpp"
            driver.write_text(
                '#include <capstone/capstone.h>\n#include <iostream>\n#include <string>\n'
                'extern "C" arm64_insn AArch64_map_insn(const char *);\n'
                'int main(){std::string s;while(std::getline(std::cin,s))'
                'std::cout<<unsigned(AArch64_map_insn(s.c_str()))<<"\\n";}\n',
                encoding="utf-8",
            )
            outputs = []
            for index, library in enumerate((baseline, optimized)):
                binary = work / f"compare-{index}"
                subprocess.run(
                    [
                        cxx,
                        "-arch",
                        "arm64",
                        "-isysroot",
                        sdk,
                        "-I" + str(SOURCE / "include"),
                        str(driver),
                        str(library),
                        "-o",
                        str(binary),
                    ],
                    check=True,
                )
                outputs.append(
                    subprocess.check_output(
                        [str(binary)], input="\n".join(inputs) + "\n", text=True
                    )
                )
            self.assertEqual(outputs[0], outputs[1])


if __name__ == "__main__":
    unittest.main()
