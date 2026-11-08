#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Validate APK anchors/pins with real TLS on ART; keys/certs remain under build/."""
import base64
import fcntl
import hashlib
import http.server
import json
import shutil
import ssl
import subprocess
import threading
from typing import ClassVar

from art_smoke import HOST, JARS, NATIVE, ROOT, environment
from bootstrap_boot import compiler
from build_apk_fixture import TOOLS, tools
from probe_tls_fixtures import generate


class Handler(http.server.BaseHTTPRequestHandler):
    requests: ClassVar[list[str]] = []

    def do_GET(self):
        self.requests.append(self.path)
        self.send_response(200)
        self.send_header('Content-Length', '1')
        self.end_headers()
        self.wfile.write(b'*')
        self.wfile.flush()
        try:
            self.connection.unwrap()  # Send close_notify; the client verifies clean EOF.
        except (OSError, ssl.SSLError):
            pass

    def log_message(self, *args):
        pass


def openssl(*args):
    return subprocess.check_output(['openssl', *map(str, args)], timeout=30, stderr=subprocess.DEVNULL)


def fixtures(work):
    resources = work / 'res'
    if resources.exists():
        shutil.rmtree(resources)
    raw, xml = resources / 'raw', resources / 'xml'
    raw.mkdir(parents=True)
    xml.mkdir()
    pins = {}
    for name in ('a', 'b', 'c'):
        assets = work / name
        generate(assets)
        cert = assets / 'loopback-cert.pem'
        (raw / f'root_{name}.der').write_bytes(openssl('x509', '-in', cert, '-outform', 'DER'))
        key = assets / 'public.pem'
        key.write_bytes(openssl('x509', '-in', cert, '-pubkey', '-noout'))
        der = openssl('pkey', '-pubin', '-in', key, '-outform', 'DER')
        pins[name] = base64.b64encode(hashlib.sha256(der).digest()).decode()
        pins[name + '512'] = base64.b64encode(hashlib.sha512(der).digest()).decode()
    # A cross-signed extra root is not required when its public key is already
    # a trust anchor. Building a valid path must ignore that irrelevant branch.
    for name in ('a', 'b', 'c'):
        (work / name / 'signing-key.pem').write_bytes(openssl('pkey', '-inform', 'DER', '-in', work / name / 'loopback-key.der'))
    ca_extensions = work / 'ca.ext'
    ca_extensions.write_text('basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n')
    openssl('x509', '-x509toreq', '-in', work / 'a/loopback-cert.pem', '-signkey', work / 'a/signing-key.pem', '-out', work / 'cross.csr')
    openssl('x509', '-req', '-in', work / 'cross.csr', '-CA', work / 'c/loopback-cert.pem', '-CAkey', work / 'c/signing-key.pem', '-CAcreateserial', '-days', '2', '-extfile', ca_extensions, '-out', work / 'cross.pem')
    openssl('req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', work / 'leaf-key.pem', '-out', work / 'leaf.csr', '-subj', '/CN=localhost')
    leaf_extensions = work / 'leaf.ext'
    leaf_extensions.write_text('basicConstraints=critical,CA:FALSE\nsubjectAltName=DNS:localhost\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n')
    openssl('x509', '-req', '-in', work / 'leaf.csr', '-CA', work / 'a/loopback-cert.pem', '-CAkey', work / 'a/signing-key.pem', '-CAcreateserial', '-days', '2', '-extfile', leaf_extensions, '-out', work / 'leaf.pem')
    (raw / 'leaf.der').write_bytes(openssl('x509', '-in', work / 'leaf.pem', '-outform', 'DER'))
    (work / 'chain.pem').write_bytes((work / 'leaf.pem').read_bytes() + (work / 'cross.pem').read_bytes())
    leaf_extensions.write_text(leaf_extensions.read_text().replace('serverAuth', 'clientAuth'))
    openssl('x509', '-req', '-in', work / 'leaf.csr', '-CA', work / 'a/loopback-cert.pem', '-CAkey', work / 'a/signing-key.pem', '-CAcreateserial', '-days', '2', '-extfile', leaf_extensions, '-out', work / 'wrong-purpose.pem')
    for name in ('a', 'b', 'c'):
        (work / name / 'signing-key.pem').unlink()
    (raw / 'roots.pem').write_bytes((work / 'a/loopback-cert.pem').read_bytes() + (work / 'b/loopback-cert.pem').read_bytes())
    (raw / 'invalid.pem').write_text('not a certificate')

    def anchors(source, extra=''):
        return f'<trust-anchors><certificates src="{source}" {extra}/></trust-anchors>'

    def pin(name='a', extra='', algorithm='SHA-256'):
        return f'<pin-set {extra}><pin digest="{algorithm}">{pins[name]}</pin></pin-set>'

    def domain(trust, pins_xml='', name='localhost'):
        return f'<domain-config><domain includeSubdomains="true">{name}</domain>{trust}{pins_xml}</domain-config>'

    a, b = anchors('@raw/root_a'), anchors('@raw/root_b')
    configs = {
        'custom': f'<base-config>{a}</base-config>',
        'leaf': f'<base-config>{anchors("@raw/leaf")}</base-config>',
        'system': f'<base-config>{anchors("system")}</base-config>',
        'domain': f'<base-config>{b}</base-config>' + domain(a, pin()),
        'mismatch': domain(a, pin('b')),
        'sha512': domain(a, pin('a512', algorithm='SHA-512')),
        'backup': domain(a, pin('b').replace('</pin-set>', f'<pin digest="SHA-256">{pins["a"]}</pin></pin-set>')),
        'expired': domain(a, pin('b', 'expiration="2000-01-01"')),
        'empty_pins': domain(a, pin('b') + domain('', '<pin-set/>')),
        'inherited_pins': domain(a, pin('b') + domain('')),
        'override_pins': domain(anchors('@raw/root_a', 'overridePins="true"'), pin('b')),
        'multi_pem': f'<base-config>{anchors("@raw/roots")}</base-config>',
        'system_custom': '<base-config><trust-anchors><certificates src="system"/><certificates src="@raw/root_a"/></trust-anchors></base-config>',
        'debug': domain(b, pin('b')) + f'<debug-overrides>{a}</debug-overrides>',
        'debug_user': domain(a, pin()) + f'<debug-overrides>{anchors("user")}</debug-overrides>',
        'debug_no_override': domain(b, pin('b')) + '<debug-overrides>' + anchors('@raw/root_a', 'overridePins="false"') + '</debug-overrides>',
        'invalid_cert': f'<base-config>{anchors("@raw/invalid")}</base-config>',
        'invalid_date': domain(a, pin('a', 'expiration="2026-99-99"')),
        'invalid_pin': domain(a, '<pin-set><pin digest="SHA-256">invalid</pin></pin-set>'),
        'invalid_digest': domain(a, pin(algorithm='MD5')),
        'invalid_base_pin': f'<base-config>{a}{pin()}</base-config>',
    }
    # Child domains inherit policy even when unrelated to their parent's name.
    configs['empty_pins'] = configs['empty_pins'].replace('<domain includeSubdomains="true">localhost</domain>', '<domain includeSubdomains="true">parent.test</domain>', 1)
    configs['inherited_pins'] = configs['inherited_pins'].replace('<domain includeSubdomains="true">localhost</domain>', '<domain includeSubdomains="true">parent.test</domain>', 1)
    for name, content in configs.items():
        (xml / f'{name}.xml').write_text('<network-security-config>' + content + '</network-security-config>')
    return resources


def main():
    work = ROOT / 'build/network-trust-smoke'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    for name in ('result.json', 'stdout.log', 'stderr.log'):
        (work / name).unlink(missing_ok=True)
    tools()
    resources = fixtures(work)
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.shiraz.networktrust"><uses-sdk android:minSdkVersion="27" android:targetSdkVersion="35"/><application android:networkSecurityConfig="@xml/domain"/></manifest>')
    compiled, apk = work / 'resources.zip', work / 'policy.apk'
    subprocess.run([str(TOOLS / 'aapt2'), 'compile', '--dir', str(resources), '-o', str(compiled)], check=True, timeout=30)
    subprocess.run([str(TOOLS / 'aapt2'), 'link', '-o', str(apk), '--manifest', str(manifest), '-I', str(TOOLS / 'android.jar'), str(compiled)], check=True, timeout=30)
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '8', '-cp', str(ROOT / 'build/framework/classes') + ':' + str(TOOLS / 'android.jar'), '-d', str(classes),
                    str(ROOT / 'tests/java/ApplicationTrustSmoke.java'), str(ROOT / 'tests/api-probe/TlsEngineChecks.java'), str(ROOT / 'tests/api-probe/NetworkChecks.java')], check=True, timeout=30)
    jar, dex = work / 'classes.jar', work / 'smoke.jar'
    subprocess.run(['jar', 'cf', str(jar), '-C', str(classes), '.'], check=True, timeout=30)
    subprocess.run(['java', '-cp', str(compiler(ROOT)), 'com.android.tools.r8.D8', '--min-api', '27', '--output', str(dex), str(jar)], check=True, timeout=60)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    key = work / 'leaf-key.pem'
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(work / 'chain.pem', key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    command = [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS), '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat',
               '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'), '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(dex),
               'org.shiraz.apiprobe.ApplicationTrustSmoke', str(apk), str(server.server_port), str(work)]
    try:
        result = subprocess.run(command, check=False, env=environment(work), text=True, capture_output=True, timeout=90)
        (work / 'stdout.log').write_text(result.stdout)
        (work / 'stderr.log').write_text(result.stderr)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()
        key.unlink(missing_ok=True)
    passed = result.returncode == 0 and 'APPLICATION_TRUST_VERIFIED 32' in result.stdout and Handler.requests == ['/body'] * 20
    (work / 'result.json').write_text(json.dumps({'runtime': 'art', 'returncode': result.returncode, 'requests': Handler.requests, 'passed': passed}, indent=2) + '\n')
    if not passed:
        raise SystemExit('Application trust smoke failed; inspect build/network-trust-smoke/')
    print('APPLICATION_TRUST_VERIFIED art (anchors, pins, domain boundaries, debug overrides, engines)')


if __name__ == '__main__':
    main()
