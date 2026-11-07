#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""ld64 exported-symbols lists for Shiraz JNI dylibs, derived from Java sources.

Each Java source file named on the command line contributes one Mach-O symbol
per `native` method: `_Java_<mangled class>_<mangled method>` (JNI short name).
Two native methods with the same name in one class would need JNI long names;
that is rejected rather than guessed. `--jni-onload` adds `_JNI_OnLoad`.

  darwin_jni_exports.py [--jni-onload] JAVA...                print the list
  darwin_jni_exports.py --check LIST [--jni-onload] JAVA...   compare with LIST
  darwin_jni_exports.py --check LIST --symbols NM JAVA...     also compare with
      NM, the `nm -gU` output of the linked dylib (address, type, name lines)
"""
import argparse
import re
import sys
from pathlib import Path

COMMENTS = re.compile(r'//[^\n]*|/\*.*?\*/', re.S)
STRINGS = re.compile(r'"(?:\\.|[^"\\\n])*"')
PACKAGE = re.compile(r'\bpackage\s+([\w.]+)\s*;')
NATIVE = re.compile(r'\bnative\b[^;{}()=]*?\b([A-Za-z_$][\w$]*)\s*\(')
# A nested class would need its outer class in the JNI name; none is supported.
NESTED = re.compile(r'\b(?:class|interface|enum)\s+\w+')


def mangle(name):
    """JNI name mangling for one identifier component."""
    out = []
    for char in name:
        if char.isascii() and char.isalnum():
            out.append(char)
        elif char == '_':
            out.append('_1')
        else:
            out.append('_0%04x' % ord(char))
    return ''.join(out)


def symbols(path):
    source = STRINGS.sub('""', COMMENTS.sub(' ', Path(path).read_text(encoding='utf-8')))
    package = PACKAGE.search(source)
    if len(NESTED.findall(source)) != 1:
        raise SystemExit(f'{path}: expected exactly one top-level type and no nested types')
    parts = (package.group(1).split('.') if package else []) + [Path(path).stem]
    owner = '_'.join(mangle(part) for part in parts)
    names = NATIVE.findall(source)
    if not names:
        raise SystemExit(f'{path}: declares no native methods')
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise SystemExit(f'{path}: overloaded native methods need JNI long names: {", ".join(duplicates)}')
    return [f'_Java_{owner}_{mangle(name)}' for name in names]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('java', nargs='+')
    parser.add_argument('--jni-onload', action='store_true')
    parser.add_argument('--check', metavar='LIST')
    parser.add_argument('--symbols', metavar='NM')
    args = parser.parse_args()
    expected = sorted({symbol for path in args.java for symbol in symbols(path)}
                      | ({'_JNI_OnLoad'} if args.jni_onload else set()))
    if not args.check:
        print('\n'.join(expected))
        return 0
    listed = sorted(line.strip() for line in Path(args.check).read_text(encoding='utf-8').splitlines()
                    if line.strip() and not line.startswith('#'))
    failures = []
    if listed != expected:
        failures.append(f'{args.check} differs from the Java native declarations:'
                        f' missing {sorted(set(expected) - set(listed))}, extra {sorted(set(listed) - set(expected))}')
    if args.symbols:
        exported = sorted(line.split()[-1] for line in Path(args.symbols).read_text(encoding='utf-8').splitlines()
                          if line.strip())
        if exported != expected:
            failures.append(f'{args.symbols} (nm -gU) differs from the exports list:'
                            f' missing {sorted(set(expected) - set(exported))}, extra {sorted(set(exported) - set(expected))}')
    for failure in failures:
        print(failure, file=sys.stderr)
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
