#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Test ART HTTP, TLS verification, and digest providers against loopback servers."""
import fcntl
import http.server
import json
from pathlib import Path
import subprocess
import ssl
import tempfile
import threading
import time
from art_smoke import HOST, JARS, ROOT, environment

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/redirect':
            self.send_response(302)
            self.send_header('Location', '/body')
            self.end_headers()
        elif self.path == '/slow':
            self.send_response(200)
            self.send_header('Content-Length', '2')
            self.end_headers()
            try:
                self.wfile.write(b'a')
                self.wfile.flush()
                time.sleep(.3)
                self.wfile.write(b'b')
            except (OSError, ssl.SSLError):
                pass
        elif self.path == '/body':
            body = b'shiraz-network-body'
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)
    def log_message(self, *args):
        pass

work = ROOT / 'build/network-smoke'
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '--release', '8', '-d', str(classes), str(ROOT / 'tests/java/NetworkSmoke.java')], check=True)
jar = work / 'smoke.jar'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output='+str(jar), str(classes)], env=environment(work), check=True)
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
temporary = tempfile.TemporaryDirectory(dir=work)
tls_dir = Path(temporary.name)
key, cert = tls_dir/'key.pem', tls_dir/'cert.pem'
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
tls_server = http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
ssl_context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ssl_context.load_cert_chain(cert,key)
tls_server.socket=ssl_context.wrap_socket(tls_server.socket,server_side=True)
tls_thread=threading.Thread(target=tls_server.serve_forever,daemon=True)
tls_thread.start()
server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    command = [str(vm), '-Xbootclasspath:'+':'.join(str(HOST/'framework'/name) for name in JARS), '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat', '-Xcompiler:'+str(HOST/'bin/dex2oat'), '-Djava.library.path='+str(HOST/'lib64'), '-cp', str(jar), 'NetworkSmoke', f'http://127.0.0.1:{server.server_port}', f'https://localhost:{tls_server.server_port}', str(cert)]
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=90)
    (work/'stdout.log').write_text(result.stdout)
    (work/'stderr.log').write_text(result.stderr)
    if result.returncode or 'NETWORK_HTTP_TLS_DIGEST_OK' not in result.stdout.splitlines():
        raise SystemExit('ART network smoke failed; inspect build/network-smoke logs')
finally:
    server.shutdown()
    server.server_close()
    thread.join()
    tls_server.shutdown()
    tls_server.server_close()
    tls_thread.join()
    temporary.cleanup()
(work/'result.json').write_text(json.dumps({'runtime':'art','http':True,'digests':['MD5','SHA-256'],'tls_verified':True,'untrusted_and_wrong_hostname_rejected':True})+'\n')
print('NETWORK_HTTP_TLS_DIGEST_OK art (trusted TLS, untrusted and mismatched certificates checked)')
