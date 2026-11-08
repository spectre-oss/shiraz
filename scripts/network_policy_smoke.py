#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Validate Android cleartext policy and wire-level denial on ART."""
import fcntl
import http.server
import json
import socket
import subprocess
import threading
from typing import ClassVar

from art_smoke import HOST, JARS, NATIVE, ROOT, environment
from bootstrap_boot import compiler
from build_apk_fixture import TOOLS, tools


class Handler(http.server.BaseHTTPRequestHandler):
    requests: ClassVar[list[str]] = []

    def do_GET(self):
        self.requests.append(self.path)
        self.send_response(200)
        self.send_header('Content-Length', '1')
        self.end_headers()
        self.wfile.write(b'*')

    def log_message(self, *args):
        pass


class Ipv6Server(http.server.ThreadingHTTPServer):
    address_family = socket.AF_INET6


def main():
    work = ROOT / 'build/network-policy-smoke'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    (work / 'result.json').unlink(missing_ok=True)
    tools()
    resources = work / 'resources.zip'
    apk = work / 'policy.apk'
    source = ROOT / 'tests/network-policy'
    subprocess.run([str(TOOLS / 'aapt2'), 'compile', '--dir', str(source / 'res'),
                    '-o', str(resources)], check=True, timeout=30)
    subprocess.run([str(TOOLS / 'aapt2'), 'link', '-o', str(apk), '--manifest',
                    str(source / 'AndroidManifest.xml'), '-I', str(TOOLS / 'android.jar'),
                    str(resources)], check=True, timeout=30)
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '8', '-cp', str(ROOT / 'build/framework/classes') + ':' + str(TOOLS / 'android.jar'),
                    '-d', str(classes), str(ROOT / 'tests/java/NetworkPolicySmoke.java'),
                    str(ROOT / 'tests/java/NetworkConfigSmoke.java')], check=True, timeout=30)
    jar = work / 'classes.jar'
    subprocess.run(['jar', 'cf', str(jar), '-C', str(classes), '.'], check=True, timeout=30)
    dex = work / 'smoke.jar'
    subprocess.run(['java', '-cp', str(compiler(ROOT)), 'com.android.tools.r8.D8', '--min-api', '27',
                    '--output', str(dex), str(jar)], check=True, timeout=60)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    try:
        ipv6_server = Ipv6Server(('::1', 0), Handler)
    except OSError:
        server.server_close()
        raise
    threads = [threading.Thread(target=instance.serve_forever, daemon=True) for instance in (server, ipv6_server)]
    for thread in threads:
        thread.start()
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    command = [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
               '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat',
               '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
               '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(dex),
               'NetworkPolicySmoke', f'http://127.0.0.1:{server.server_port}', str(apk), f'http://[::1]:{ipv6_server.server_port}']
    try:
        result = subprocess.run(command, check=False, env=environment(work), text=True,
                                capture_output=True, timeout=30)
        (work / 'stdout.log').write_text(result.stdout)
        (work / 'stderr.log').write_text(result.stderr)
    finally:
        for instance in (server, ipv6_server):
            instance.shutdown()
            instance.server_close()
        for thread in threads:
            thread.join()
    (work / 'result.json').write_text(json.dumps({'runtime': 'art', 'returncode': result.returncode,
        'requests': Handler.requests, 'passed': result.returncode == 0
        and 'NETWORK_POLICY_VERIFIED' in result.stdout and Handler.requests == ['/body', '/xml-body', '/ipv6-body']}, indent=2) + '\n')
    if result.returncode or 'NETWORK_POLICY_VERIFIED' not in result.stdout or Handler.requests != ['/body', '/xml-body', '/ipv6-body']:
        raise SystemExit('Network policy smoke failed; inspect build/network-policy-smoke/')
    print('NETWORK_POLICY_VERIFIED art (denied request never reached server)')


if __name__ == '__main__':
    main()
