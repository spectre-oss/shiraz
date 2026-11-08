#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Validate Android cleartext policy and wire-level denial on ART."""
import fcntl
import http.server
import json
import subprocess
import threading
from typing import ClassVar

from art_smoke import HOST, JARS, NATIVE, ROOT, environment
from bootstrap_boot import compiler


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


def main():
    work = ROOT / 'build/network-policy-smoke'
    work.mkdir(parents=True, exist_ok=True)
    lock = (work / '.lock').open('a')
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    (work / 'result.json').unlink(missing_ok=True)
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '--release', '8', '-cp', str(ROOT / 'build/framework/classes'),
                    '-d', str(classes), str(ROOT / 'tests/java/NetworkPolicySmoke.java')], check=True, timeout=30)
    jar = work / 'classes.jar'
    subprocess.run(['jar', 'cf', str(jar), '-C', str(classes), '.'], check=True, timeout=30)
    dex = work / 'smoke.jar'
    subprocess.run(['java', '-cp', str(compiler(ROOT)), 'com.android.tools.r8.D8', '--min-api', '27',
                    '--output', str(dex), str(jar)], check=True, timeout=60)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    vm = HOST / 'bin/dalvikvm64'
    if not vm.is_file():
        vm = HOST / 'bin/dalvikvm'
    command = [str(vm), '-Xbootclasspath:' + ':'.join(str(HOST / 'framework' / name) for name in JARS),
               '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat',
               '-Djava.library.path=' + str(NATIVE) + ':' + str(HOST / 'lib64'),
               '-cp', str(ROOT / 'build/shiraz-framework.jar') + ':' + str(dex),
               'NetworkPolicySmoke', f'http://127.0.0.1:{server.server_port}']
    try:
        result = subprocess.run(command, check=False, env=environment(work), text=True,
                                capture_output=True, timeout=30)
        (work / 'stdout.log').write_text(result.stdout)
        (work / 'stderr.log').write_text(result.stderr)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()
    (work / 'result.json').write_text(json.dumps({'runtime': 'art', 'returncode': result.returncode,
        'requests': Handler.requests, 'passed': result.returncode == 0
        and 'NETWORK_POLICY_VERIFIED' in result.stdout and Handler.requests == ['/body']}, indent=2) + '\n')
    if result.returncode or 'NETWORK_POLICY_VERIFIED' not in result.stdout or Handler.requests != ['/body']:
        raise SystemExit('Network policy smoke failed; inspect build/network-policy-smoke/')
    print('NETWORK_POLICY_VERIFIED art (denied request never reached server)')


if __name__ == '__main__':
    main()
