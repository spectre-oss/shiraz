# SPDX-License-Identifier: GPL-3.0-only
"""Generate disposable loopback TLS material under build/, never source assets."""
import subprocess
from pathlib import Path


def generate(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    key = directory / 'loopback-key.pem'
    cert = directory / 'loopback-cert.pem'
    subprocess.run(
        ['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
         '-keyout', str(key), '-out', str(cert), '-days', '2',
         '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost,IP:::1',
         '-addext', 'basicConstraints=critical,CA:TRUE'],
        check=True, timeout=30, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    try:
        subprocess.run(
            ['openssl', 'pkcs8', '-topk8', '-nocrypt', '-in', str(key),
             '-outform', 'DER', '-out', str(directory / 'loopback-key.der')],
            check=True, timeout=10, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    finally:
        key.unlink(missing_ok=True)
