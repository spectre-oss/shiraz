#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build optional legacy Apache HTTP DEX from locally supplied upstream jars."""

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'src'))
from shiraz.host_layout import host_out_dir  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, action='append', required=True)
    parser.add_argument('--notice', type=Path, action='append', required=True)
    parser.add_argument('--smoke', action='store_true')
    args = parser.parse_args()
    output = ROOT / 'build/shiraz-legacy-http.jar'
    output.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as directory:
        work = Path(directory)
        classes = work / 'classes'
        classes.mkdir()
        sources = sorted((ROOT / 'optional-java/legacy-http').rglob('*.java'))
        subprocess.run(
            [
                'javac',
                '--release',
                '8',
                '-cp',
                ':'.join([str(ROOT / 'build/framework/classes'), *map(str, args.jar)]),
                '-d',
                str(classes),
                *map(str, sources),
            ],
            check=True,
        )
        dex = work / 'classes.dex'
        subprocess.run(
            [
                str(host_out_dir(ROOT / 'context/art_standalone') / 'bin/dx'),
                '--dex',
                '--min-sdk-version=27',
                '--output=' + str(dex),
                *map(str, args.jar),
                str(classes),
            ],
            check=True,
        )
        with zipfile.ZipFile(work / 'library.jar', 'w') as archive:
            archive.write(dex, 'classes.dex')
            for index, jar in enumerate(args.jar):
                with zipfile.ZipFile(jar) as upstream:
                    for name in upstream.namelist():
                        if name.upper().startswith(
                            ('META-INF/LICENSE', 'META-INF/NOTICE')
                        ) and not name.endswith('/'):
                            archive.writestr(
                                'META-INF/upstream/' + str(index) + '/' + Path(name).name,
                                upstream.read(name),
                            )
            for index, notice in enumerate(args.notice):
                archive.write(notice, 'META-INF/licenses/' + str(index) + '-' + notice.name)
            provenance = [
                {'name': jar.name, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
                for jar in args.jar
            ]
            archive.writestr('META-INF/provenance.json', json.dumps(provenance, indent=2) + '\n')
        if args.smoke:
            tests = work / 'tests'
            tests.mkdir()
            classpath = ':'.join(
                [str(classes), str(ROOT / 'build/framework/classes'), *map(str, args.jar)]
            )
            subprocess.run(
                [
                    'javac',
                    '--release',
                    '8',
                    '-cp',
                    classpath,
                    '-d',
                    str(tests),
                    str(ROOT / 'tests/java/AndroidHttpClientSmoke.java'),
                ],
                check=True,
            )
            subprocess.run(
                ['java', '-cp', str(tests) + ':' + classpath, 'AndroidHttpClientSmoke'],
                check=True,
                timeout=30,
            )
        shutil.move(work / 'library.jar', output)


if __name__ == '__main__':
    main()
