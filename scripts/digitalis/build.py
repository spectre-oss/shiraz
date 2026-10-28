#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the experimental optional Digitalis bridge, never execute an APK."""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/digitalis-host'
SOURCES = {
    'digitalis': '8ab8e93225ce10e10ef411de7e377dda6673c335',
    'digitalis-native-bridge-support': 'b41bf84a958aad713dd7f1e3e0b9f39a03701b4d',
    'digitalis-bionic': '731631f300090436d7f5df80d50b6275c8c60a93',
}

def main():
    for name, revision in SOURCES.items():
        checkout = ROOT / 'context' / name
        if not checkout.is_dir():
            raise SystemExit('Missing pinned source checkout: ' + name)
        actual = subprocess.check_output(['git', '-C', str(checkout), 'rev-parse', 'HEAD'], text=True).strip()
        if actual != revision:
            raise SystemExit('Unexpected source revision: ' + name)
    (WORK / 'tmp').mkdir(parents=True, exist_ok=True)
    scripts = pathlib.Path(__file__).parent
    subprocess.run([sys.executable, str(scripts / 'prepare.py')], check=True)
    overlays = [
        ('intrinsics/all_to_x86_32_or_x86_64/include', 'berberis/intrinsics/all_to_x86_32_or_x86_64/verifier_assembler_x86_32_or_x86_64.h',
         '    constexpr Register(std::optional<Register> reg)',
         '    template <class T> requires std::is_same_v<std::remove_cvref_t<T>, std::optional<Register>>\n    constexpr Register(T&& reg)'),
        ('base/include', 'berberis/base/maps_snapshot.h',
         '  struct Record {',
         '  struct Record {\n    Record(uintptr_t first, uintptr_t last, ArenaString path)\n        : start(first), end(last), pathname(std::move(path)) {}'),
    ]
    for base, relative, before, after in overlays:
        source = (ROOT / 'context/digitalis' / base / relative).read_text()
        if source.count(before) != 1:
            raise SystemExit('Unexpected overlay source: ' + relative)
        output = WORK / 'gen' / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(source.replace(before, after))
    subprocess.run([sys.executable, str(scripts / 'compile_generator.py')], check=True)
    subprocess.run([sys.executable, str(scripts / 'compile.py')], check=True)
    subprocess.run([sys.executable, str(scripts / 'host_library_names.py'),
                    str(ROOT / 'context/art_standalone/out/host/linux-x86/lib64'),
                    str(ROOT / '.local/lib')], check=True)

if __name__ == '__main__':
    main()
