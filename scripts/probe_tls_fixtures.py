# SPDX-License-Identifier: GPL-3.0-only
"""Generate disposable loopback TLS material under build/, never source assets."""

import subprocess
from pathlib import Path

_OPENSSL_CONFIG = """\
[req]
distinguished_name = dn
x509_extensions = v3_ext
prompt = no

[dn]
CN = localhost

[v3_ext]
subjectAltName = DNS:localhost,IP:::1
basicConstraints = critical,CA:TRUE
"""


def generate(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    key = directory / 'loopback-key.pem'
    cert = directory / 'loopback-cert.pem'
    config = directory / 'loopback-openssl.cnf'
    config.write_text(_OPENSSL_CONFIG, encoding='utf-8')
    try:
        subprocess.run(
            [
                'openssl',
                'req',
                '-x509',
                '-newkey',
                'rsa:2048',
                '-nodes',
                '-config',
                str(config),
                '-keyout',
                str(key),
                '-out',
                str(cert),
                '-days',
                '2',
            ],
            check=True,
            timeout=30,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.run(
            [
                'openssl',
                'pkcs8',
                '-topk8',
                '-nocrypt',
                '-in',
                str(key),
                '-outform',
                'DER',
                '-out',
                str(directory / 'loopback-key.der'),
            ],
            check=True,
            timeout=10,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    finally:
        key.unlink(missing_ok=True)
        config.unlink(missing_ok=True)
