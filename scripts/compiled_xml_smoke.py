#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise compiled resource XML cursors and typed attributes on OpenJDK and ART."""

import fcntl
import json
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

from art_smoke import DARWIN, HOST, JARS, NATIVE, environment

ROOT = Path(__file__).resolve().parents[1]
work = ROOT / 'build/compiled-xml-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
try:
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    raise SystemExit('Compiled XML smoke is already running')
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
framework = ROOT / 'build/framework/classes'
core = (
    ROOT
    / 'context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
)
classpath = str(framework) + ':' + str(core)
subprocess.run(
    [
        'javac',
        '--release',
        '8',
        '-cp',
        str(ROOT / '.deps/android-sdk/android.jar'),
        '-d',
        str(classes),
        str(ROOT / 'tests/apk/src/org/shiraz/fixture/CompiledXmlFixture.java'),
    ],
    check=True,
)
subprocess.run(
    [
        'javac',
        '--release',
        '8',
        '-cp',
        str(classes) + ':' + classpath,
        '-d',
        str(classes),
        str(ROOT / 'tests/java/CompiledXmlSmoke.java'),
    ],
    check=True,
)
commands = [
    (
        'openjdk',
        [
            'java',
            '-Xcheck:jni',
            '-Djava.library.path=' + str(NATIVE / 'jvm-native' if DARWIN else NATIVE),
            '-cp',
            str(classes) + ':' + classpath,
            'CompiledXmlSmoke',
        ],
    )
]
dex = work / 'classes.dex'
subprocess.run(
    [str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output=' + str(dex), str(classes)],
    env=environment(work),
    check=True,
)
jar = work / 'smoke.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    entry = zipfile.ZipInfo('classes.dex')
    padding = (-(30 + len(entry.filename) + 4)) % 4
    entry.extra = struct.pack('<HH', 0xFFFF, padding) + b'\0' * padding
    archive.writestr(entry, dex.read_bytes())
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
commands.append(
    (
        'art',
        [
            str(vm),
            '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
            '-Xcheck:jni',
            '-Xusejit:false',
            '-Xint',
            '-Xnoimage-dex2oat',
            '-Xcompiler:' + str(HOST / 'bin/dex2oat'),
            '-Djava.library.path=' + str(NATIVE),
            '-cp',
            str(ROOT / 'build/shiraz-framework.jar') + ':' + str(jar),
            'CompiledXmlSmoke',
        ],
    )
)
# Rename only trusted synthetic fixture entries; retain resource-table offsets.
extensionless = work / 'extensionless.apk'
with zipfile.ZipFile(ROOT / 'build/apk-fixture/fixture.apk') as source:
    renamed = {
        name: name.rsplit('.', 1)[0] + '_' + name.rsplit('.', 1)[1]
        for name in source.namelist()
        if Path(name).name in ('png_grid.png', 'shape_solid.xml')
    }
    assert len(renamed) >= 2 and len(set(renamed.values())) == len(renamed)
    assert not set(renamed.values()).intersection(source.namelist())
    table = source.read('resources.arsc')
    for old, new in renamed.items():
        matches = 0
        for encoding in ('utf-8', 'utf-16le'):
            before, after = old.encode(encoding), new.encode(encoding)
            assert len(before) == len(after)
            matches += table.count(before)
            table = table.replace(before, after)
        assert matches > 0, old
    with zipfile.ZipFile(extensionless, 'w') as target:
        for entry in source.infolist():
            payload = table if entry.filename == 'resources.arsc' else source.read(entry)
            entry.filename = renamed.get(entry.filename, entry.filename)
            target.writestr(entry, payload)
marker = 'COMPILED_XML_OWNERSHIP_OK'
for runtime, command in commands:
    command.insert(
        1, '-Dshiraz.frameworkResources=' + str(ROOT / 'build/framework-res/framework-res.apk')
    )
    command.append(str(ROOT / 'build/apk-fixture/fixture.apk'))
    command.append(str(extensionless))
    result = subprocess.run(
        command, env=environment(work), capture_output=True, text=True, timeout=30
    )
    (work / (runtime + '-stdout.log')).write_text(result.stdout)
    (work / (runtime + '-stderr.log')).write_text(result.stderr)
    if result.returncode or marker not in result.stdout.splitlines():
        raise SystemExit(
            'Compiled XML smoke failed in '
            + runtime
            + '; inspect build/compiled-xml-smoke/'
            + runtime
            + '-stderr.log'
        )
    print('COMPILED_XML_OK ' + runtime)
(work / 'result.json').write_text(
    json.dumps({'runtimes': ['openjdk', 'art'], 'marker': marker, 'component_only': True}, indent=2)
    + '\n'
)
