#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Execute shared networking assertions (optionally from the API probe APK) on ART."""
import argparse
import fcntl
import hashlib
import json
import subprocess
from pathlib import Path

from art_smoke import HOST, JARS, NATIVE, ROOT, environment
from bootstrap_boot import compiler
from probe_tls_fixtures import generate as tls_fixtures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, help='Run networking classes from this built API probe APK')
    parser.add_argument('--url', help='Optional public HTTPS download; separate from deterministic loopback proof')
    args = parser.parse_args()
    work = ROOT / 'build/network-probe-runtime'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    (work / 'result.json').unlink(missing_ok=True)
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    apk = args.apk.resolve() if args.apk else None
    assets = work / 'assets'
    tls_fixtures(assets)
    framework = ROOT / 'build/shiraz-framework.jar'
    interfaces = HOST.parent / 'common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar'
    source = ROOT / 'tests/api-probe-host/NetworkRuntimeMain.java'
    subprocess.run(['javac', '--release', '8', '-cp', str(ROOT / 'build/framework/classes') + ':' + str(interfaces) + ':' + str(ROOT / '.deps/android-sdk/android.jar'), '-d', str(classes), str(source),
                    str(ROOT / 'tests/api-probe/NetworkChecks.java'),
                    str(ROOT / 'tests/api-probe/TlsNetworkChecks.java'),
                    str(ROOT / 'tests/api-probe/TlsEngineChecks.java'),
                    str(ROOT / 'tests/api-probe/LocalSocketChecks.java'),
                    str(ROOT / 'tests/api-probe/NetworkApiChecks.java'),
                    str(ROOT / 'tests/api-probe/NetworkXmlChecks.java'),
                    str(ROOT / 'tests/api-probe/TrustManagerChecks.java'),
                    str(ROOT / 'tests/api-probe/TlsStreamChecks.java')], check=True, timeout=30)
    jar = work / 'runner.jar'
    subprocess.run(['jar', 'cf', str(jar), '-C', str(classes), '.'], check=True, timeout=30)
    dex = work / 'runner-dex.jar'
    subprocess.run(['java', '-cp', str(compiler(ROOT)), 'com.android.tools.r8.D8',
                    '--min-api', '27', '--output', str(dex), str(jar)], check=True, timeout=60)
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    command = [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
               '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat',
               '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'), '-cp',
               ':'.join(map(str, (framework, *([apk] if apk else []), dex))),
               'org.shiraz.apiprobe.NetworkRuntimeMain', str(assets)]
    if args.url and not args.url.startswith('https://'):
        parser.error('--url must use HTTPS')
    command.append(args.url or '-')
    fingerprint = '-'
    if apk:
        digest = hashlib.sha256()
        source = ROOT / 'tests/api-probe'
        for path in sorted(source.rglob('*')):
            if path.is_file():
                digest.update(path.relative_to(source).as_posix().encode())
                digest.update(b'\0')
                digest.update(path.read_bytes())
                digest.update(b'\0')
        fingerprint = digest.hexdigest()
    command.append(fingerprint)
    command.append(str(apk) if apk else '-')
    result = subprocess.run(command, check=False, env=environment(work), text=True, capture_output=True, timeout=90)
    (work / 'stdout.log').write_text(result.stdout)
    (work / 'stderr.log').write_text(result.stderr)
    cases = []
    for line in result.stdout.splitlines():
        if line.startswith('NETWORK_RESULT\t'):
            _, name, status, observed = line.split('\t', 3)
            cases.append({'id': name, 'status': status, 'observed': observed})
            print(f'{status} {name}: {observed}')
    (work / 'result.json').write_text(json.dumps({'runtime': 'art',
        'apk': str(apk) if apk else None, 'internet_url': args.url,
        'internet_pass': 'NETWORK_INTERNET_PASS ' in result.stdout, 'returncode': result.returncode, 'cases': cases}, indent=2) + '\n')
    if result.returncode or len(cases) != (34 if apk else 33) or any(c['status'] != 'PASS' for c in cases):
        raise SystemExit('ART network probe failed; inspect build/network-probe-runtime/')
    for line in result.stdout.splitlines():
        if line.startswith('NETWORK_INTERNET_PASS '):
            print(line)
    print('NETWORK_PROBE_ART_PASS')


if __name__ == '__main__':
    main()
