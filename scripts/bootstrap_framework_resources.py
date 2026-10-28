#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build an AOSP resource-only dependency closure; all third-party output is ignored."""
import argparse
import fcntl
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile
from shiraz.framework_resources import ResourceIndex
from build_apk_fixture import DOWNLOADS

ROOT = Path(__file__).resolve().parents[1]
COMMIT = '7e7ea0991020d15cdb69c5da933c282c92f884ef'
URL = 'https://android.googlesource.com/platform/frameworks/base/+archive/' + COMMIT + '/core/res.tar.gz'
CONTENT_SHA256 = '4160c9accff68d789228c201dbc4fc9c2ea2e437b7600413eb4aeab9bb3941ae'
LICENSE_SHA256 = 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30'


def download(path, url, checksum, limit):
    if not path.exists():
        temporary = path.with_suffix('.download')
        with urllib.request.urlopen(url, timeout=30) as source, temporary.open('wb') as target:
            total = 0
            while chunk := source.read(1024 * 1024):
                total += len(chunk)
                if total > limit:
                    raise ValueError('source download exceeds limit')
                target.write(chunk)
        temporary.replace(path)
    if path.stat().st_size > limit or (checksum is not None and hashlib.sha256(path.read_bytes()).hexdigest() != checksum):
        raise ValueError('framework dependency checksum mismatch')


def source_files(archive):
    files, total, names = {}, 0, set()
    with tarfile.open(archive, 'r:gz') as stream:
        for number, member in enumerate(stream):
            name = PurePosixPath(member.name)
            canonical = name.as_posix()
            if number >= 50000 or name.is_absolute() or '..' in name.parts or '\\' in member.name or canonical in names:
                raise ValueError('unsafe framework archive path/count')
            names.add(canonical)
            if member.isdir():
                continue
            total += member.size
            if not member.isfile() or member.size > 16 * 1024 * 1024 or total > 256 * 1024 * 1024:
                raise ValueError('unsafe framework archive entry/size')
            files[member.name] = stream.extractfile(member).read()
    return files


def source_content_sha256(files):
    # Gitiles can emit different gzip/tar bytes for the same pinned Git tree.
    # Hash the bounded, validated file names and contents instead.
    entries = (f'{name}\0{len(data)}\0{hashlib.sha256(data).hexdigest()}\n'
               for name, data in sorted(files.items()))
    return hashlib.sha256(''.join(entries).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed', action='append', help='type/name; repeated seeds replace defaults')
    args = parser.parse_args()
    seeds = [tuple(value.split('/', 1)) for value in (args.seed or ['style/Theme.DeviceDefault.NoActionBar', 'style/Theme.DeviceDefault.Light.NoActionBar', 'style/Theme.Material.Light.NoActionBar', 'style/Theme.Material.NoActionBar', 'style/Theme.Light.NoTitleBar', 'style/Widget.Material.Button.Colored', 'style/TextAppearance.Material.Display1', 'anim/linear_interpolator', 'anim/accelerate_decelerate_interpolator', 'anim/accelerate_interpolator', 'anim/decelerate_interpolator', 'anim/bounce_interpolator', 'anim/cycle_interpolator', 'anim/anticipate_interpolator', 'anim/overshoot_interpolator', 'anim/anticipate_overshoot_interpolator'])]
    if any(len(key) != 2 or not all(key) for key in seeds):
        parser.error('seeds must use type/name')
    cache = ROOT / '.deps/framework-res'; cache.mkdir(parents=True, exist_ok=True)
    lock = (cache / '.build.lock').open('a')
    try: fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError: raise SystemExit('A framework resource build is already running')
    archive = cache / 'android-8.1.0_r81-core-res.tar.gz'
    download(archive, URL, None, 64 * 1024 * 1024)
    license = cache / 'Apache-2.0.txt'
    download(license, 'https://www.apache.org/licenses/LICENSE-2.0.txt', LICENSE_SHA256, 32768)
    files = source_files(archive)
    if source_content_sha256(files) != CONTENT_SHA256:
        raise ValueError('framework source content checksum mismatch')
    index = ResourceIndex(files); source_count = len(index.nodes); selected, generated = index.closure(seeds)
    outputs, provenance = index.emit(selected, generated)
    build = ROOT / 'build/framework-res'; build.mkdir(parents=True, exist_ok=True)
    source = build / 'source'
    if source.exists(): shutil.rmtree(source)
    for name, data in outputs.items():
        target = source / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
    manifest = build / 'AndroidManifest.xml'; manifest.write_text('<manifest package="android"/>\n')
    tool_archive = cache / 'aapt2-linux.jar'; tool_url, tool_hash = DOWNLOADS['aapt2-linux.jar']
    download(tool_archive, tool_url, tool_hash, 16 * 1024 * 1024)
    with zipfile.ZipFile(tool_archive) as tools_zip:
        if tools_zip.getinfo('aapt2').file_size > 32 * 1024 * 1024: raise ValueError('compiler exceeds size limit')
        compiler = cache / 'aapt2'; compiler.write_bytes(tools_zip.read('aapt2')); compiler.chmod(0o755)
    compiled = build / 'compiled.zip'; apk = build / 'framework-res.pending.apk'
    subprocess.run([str(compiler), 'compile', '--dir', str(source / 'res'), '-o', str(compiled), '--legacy'], check=True, timeout=120)
    # aapt2 assigns the reserved framework package ID 0x01 to package "android".
    # Excluded public IDs must not be recycled for unrelated private resources.
    stable = build / 'stable-ids.txt'; stable.write_text(index.stable_ids())
    subprocess.run([str(compiler), 'link', '-o', str(apk), '--manifest', str(manifest),
                    '--stable-ids', str(stable), str(compiled)], check=True, timeout=120)
    dump = subprocess.run([str(compiler), 'dump', 'resources', str(apk)], check=True, capture_output=True, text=True, timeout=30).stdout
    actual = {name: int(ident, 16) for ident, name in re.findall(r'^\s*resource (0x[0-9a-fA-F]+) (\S+)', dump, re.MULTILINE)}
    index.validate_ids(actual, selected)
    report = {'source': URL, 'commit': COMMIT, 'source_sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
              'source_content_sha256': CONTENT_SHA256, 'aapt2_sha256': tool_hash, 'seeds': seeds,
              'selected_resources': len(selected), 'source_resources': source_count, 'source_files': provenance,
              'selection_scope': 'Static XML closure from explicit named seeds; not proof of arbitrary dynamic resource use',
              'public_ids': {'/'.join(key): f'0x{index.public[key]:08x}' for key in sorted(selected) if key in index.public}}
    inputs = sorted({path for paths in provenance.values() for path in paths} | {'res/values/public.xml'})
    report['input_file_sha256'] = {path: hashlib.sha256(files[path]).hexdigest() for path in inputs}
    notices = {}
    for path in inputs:
        if path.endswith('.xml'):
            for comment in re.findall(rb'<!--(.*?)-->', files[path], re.DOTALL):
                if b'copyright' in comment.lower() or b'licensed under' in comment.lower():
                    notices.setdefault(comment.strip(), []).append(path)
    source_notices = b'\n\n'.join(comment + b'\nSource files:\n' + '\n'.join(sorted(set(paths))).encode('utf-8')
                                  for comment, paths in sorted(notices.items()))
    with zipfile.ZipFile(apk, 'a') as output:
        for name, data in {'AOSP-NOTICE': files['NOTICE'], 'Apache-2.0.txt': license.read_bytes(),
                           'AOSP-SOURCE-NOTICES': source_notices,
                           'shiraz-resource-provenance.json': json.dumps(report, sort_keys=True, indent=2)}.items():
            info = zipfile.ZipInfo('META-INF/' + name, date_time=(1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, data)
    report['apk_sha256'] = hashlib.sha256(apk.read_bytes()).hexdigest(); report['apk_bytes'] = apk.stat().st_size
    (build / 'provenance.json').write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
    apk = apk.replace(build / 'framework-res.apk')
    print(f'AOSP resource closure: {len(selected)}/{len(index.nodes)} resources; {apk.stat().st_size} bytes; {apk.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
