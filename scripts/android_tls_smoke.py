#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Test the Android wolfSSL socket bridge against a bounded local TLS server."""
import argparse
import fcntl
import http.server
import json
from pathlib import Path
import subprocess
import ssl
import tempfile
import threading
from art_smoke import HOST, JARS, ROOT, environment

class Handler(http.server.BaseHTTPRequestHandler):
    requests=[]
    def do_GET(self):
        self.requests.append(self.path)
        if self.path == '/redirect':
            self.send_response(302)
            self.send_header('Location', '/body')
            self.end_headers()
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

parser=argparse.ArgumentParser()
parser.add_argument('--apk',type=Path)
args=parser.parse_args()
work = ROOT / ('build/apk-tls-smoke' if args.apk else 'build/android-tls-smoke')
work.mkdir(exist_ok=True)
lock = (work / '.lock').open('a')
fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
(work / 'result.json').unlink(missing_ok=True)
classes = work / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '--release', '8', '-cp', str(ROOT/'build/framework/classes'), '-d', str(classes), str(ROOT / 'tests/java/AndroidTlsSmoke.java')], check=True)
jar = work / 'smoke.jar'
subprocess.run([str(HOST / 'bin/dx'), '--dex', '--min-sdk-version=27', '--output='+str(jar), str(classes)], env=environment(work), check=True)
vm = HOST / 'bin/dalvikvm64'
if not vm.is_file():
    vm = HOST / 'bin/dalvikvm'
temporary = tempfile.TemporaryDirectory(dir=work)
tls_dir = Path(temporary.name)
key, cert = tls_dir/'key.pem', tls_dir/'cert.pem'
subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
class TlsServer(http.server.ThreadingHTTPServer):
    handshake_errors=[]
    def get_request(self):
        try: return super().get_request()
        except ssl.SSLError as error:
            self.handshake_errors.append(str(error))
            raise

tls_server = TlsServer(('127.0.0.1',0),Handler)
ssl_context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ssl_context.load_cert_chain(cert,key)
ssl_context.set_alpn_protocols(['http/1.1'])
sni_names=[]
ssl_context.set_servername_callback(lambda sock,name,ctx: sni_names.append(name))
tls_server.socket=ssl_context.wrap_socket(tls_server.socket,server_side=True)
tls_thread=threading.Thread(target=tls_server.serve_forever,daemon=True)
tls_thread.start()
try:
    command = [str(vm), '-Xbootclasspath:'+':'.join(str(HOST/'framework'/name) for name in JARS), '-Xint', '-Xusejit:false', '-Xnoimage-dex2oat', '-Xcompiler:'+str(HOST/'bin/dex2oat'), '-Djava.library.path='+str(HOST/'lib64'), '-cp', str(ROOT/'build/shiraz-framework.jar')+':'+str(jar), 'AndroidTlsSmoke', str(tls_server.server_port), str(cert)]
    custom=subprocess.run(command[:-2]+['custom'],env=environment(work),capture_output=True,text=True,timeout=20)
    (work/'custom-stdout.log').write_text(custom.stdout)
    (work/'custom-stderr.log').write_text(custom.stderr)
    if custom.returncode or 'CUSTOM_TLS_DEFAULTS_PRESERVED' not in custom.stdout: raise SystemExit('Custom TLS defaults test failed')
    if args.apk: command.append(str(args.apk.resolve()))
    result = subprocess.run(command, env=environment(work), capture_output=True, text=True, timeout=30)
    (work/'server.json').write_text(json.dumps({'sni':sni_names,'errors':tls_server.handshake_errors,'requests':Handler.requests},indent=2)+'\n')
    (work/'stdout.log').write_text(result.stdout)
    (work/'stderr.log').write_text(result.stderr)
    if result.returncode or 'ANDROID_TLS_BRIDGE_VERIFIED' not in result.stdout.splitlines():
        raise SystemExit('ART TLS bridge smoke failed; inspect '+str(work)+' logs')
    if args.apk: assert 'EXACT_APK_TLS_VERIFIED' in result.stdout.splitlines()
    assert 'localhost' in sni_names, sni_names
    assert '/forbidden' not in Handler.requests, Handler.requests
finally:
    tls_server.shutdown()
    tls_server.server_close()
    tls_thread.join()
    temporary.cleanup()
(work/'result.json').write_text(json.dumps({'runtime':'art','exact_apk':bool(args.apk),'bridge_default_factories':True,'trusted_body':True,'untrusted_rejected':True,'https_endpoint_explicit_and_implicit_rejected':True,'invalid_failed_session':True,'alpn':'http/1.1','sni_names':sni_names,'forbidden_http_request_observed':False},indent=2)+'\n')
print('ANDROID_TLS_BRIDGE_VERIFIED art')
