#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""ld64 exported-symbols lists for Shiraz JNI dylibs, derived from Java sources.

Each Java source file named on the command line contributes one Mach-O symbol
per `native` method: `_Java_<mangled class>_<mangled method>` (JNI short name).
Two native methods with the same name in one class would need JNI long names;
that is rejected rather than guessed. `--jni-onload` adds `_JNI_OnLoad`.
Non-JNI entry points come from `--c-source FILE` (every `extern "C"` function
defined in FILE, plus each `FORWARD(name, ...)` use, as `_name`, as compiled
for macOS: the `#else` side of `#if defined(__APPLE__)` and the body of
`#if !defined(__APPLE__)` are skipped; other conditionals keep both sides) and from
`--extra FILE` (one Mach-O symbol per line, `#` comments; for C++ functions
that other Shiraz dylibs link against).

  darwin_jni_exports.py [--jni-onload] JAVA...                print the list
  darwin_jni_exports.py --check LIST [--jni-onload] JAVA...   compare with LIST
  darwin_jni_exports.py --check LIST --symbols NM JAVA...     also compare with
      NM, the `nm -gU` output of the linked dylib (address, type, name lines)
  darwin_jni_exports.py --c-source SRC [--extra FILE] [JAVA...]   the same,
      with C entry points defined in SRC and the symbols listed in FILE
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
# A definition, not a declaration: the parameter list is followed by a body.
C_DEFINITION = re.compile(r'\bextern\s+"C"\s+(?:JNIEXPORT\s+)?[^;{}()#]*?\b([A-Za-z_]\w*)\s*\([^;{}]*\)\s*\{')
C_FORWARD = re.compile(r'^\s*FORWARD\(\s*([A-Za-z_]\w*)\s*,', re.M)
APPLE_IF = re.compile(r'#\s*(?:if\s+defined\s*\(\s*__APPLE__\s*\)|if\s+defined\s+__APPLE__|ifdef\s+__APPLE__)\s*$')
APPLE_IFNOT = re.compile(r'#\s*(?:if\s+!\s*defined\s*\(\s*__APPLE__\s*\)|if\s+!\s*defined\s+__APPLE__|ifndef\s+__APPLE__)\s*$')


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


def darwin_lines(path, source):
    """SOURCE's non-preprocessor lines as compiled with __APPLE__ defined."""
    kept, stack = [], []  # stack entries: [is_apple_conditional, active]
    for number, line in enumerate(source.splitlines(), 1):
        stripped = line.strip()
        if not stripped.startswith('#'):
            if all(active for _, active in stack):
                kept.append(line)
            continue
        directive = re.sub(r'\s+', ' ', stripped)
        if APPLE_IF.match(directive):
            stack.append([True, True])
        elif APPLE_IFNOT.match(directive):
            stack.append([True, False])
        elif re.match(r'# ?if', directive):
            stack.append([False, True])
        elif re.match(r'# ?el(?:se|if)\b', directive):
            if not stack:
                raise SystemExit(f'{path}:{number}: {stripped} without #if')
            if stack[-1][0]:
                if directive.startswith(('#elif', '# elif')):
                    raise SystemExit(f'{path}:{number}: #elif after an __APPLE__ conditional is not supported')
                stack[-1][1] = not stack[-1][1]
        elif re.match(r'# ?endif\b', directive):
            if not stack:
                raise SystemExit(f'{path}:{number}: #endif without #if')
            stack.pop()
    if stack:
        raise SystemExit(f'{path}: unterminated #if')
    return kept


def c_symbols(path):
    source = COMMENTS.sub(' ', Path(path).read_text(encoding='utf-8'))
    # Preprocessor lines (the FORWARD macro body itself) define nothing.
    source = '\n'.join(darwin_lines(path, source))
    names = C_DEFINITION.findall(source) + C_FORWARD.findall(source)
    if not names:
        raise SystemExit(f'{path}: defines no extern "C" functions')
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise SystemExit(f'{path}: extern "C" functions defined twice: {", ".join(duplicates)}')
    return [f'_{name}' for name in names]


def listed_symbols(path):
    return [line.strip() for line in Path(path).read_text(encoding='utf-8').splitlines()
            if line.strip() and not line.lstrip().startswith('#')]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('java', nargs='*')
    parser.add_argument('--jni-onload', action='store_true')
    parser.add_argument('--c-source', action='append', default=[], metavar='SRC')
    parser.add_argument('--extra', action='append', default=[], metavar='FILE')
    parser.add_argument('--check', metavar='LIST')
    parser.add_argument('--symbols', metavar='NM')
    args = parser.parse_args()
    if not (args.java or args.c_source or args.extra):
        parser.error('name at least one JAVA source, --c-source or --extra')
    expected = sorted({symbol for path in args.java for symbol in symbols(path)}
                      | {symbol for path in args.c_source for symbol in c_symbols(path)}
                      | {symbol for path in args.extra for symbol in listed_symbols(path)}
                      | ({'_JNI_OnLoad'} if args.jni_onload else set()))
    if not args.check:
        print('\n'.join(expected))
        return 0
    listed = sorted(listed_symbols(args.check))
    failures = []
    if listed != expected:
        failures.append(f'{args.check} differs from the Java native declarations and C entry points:'
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
