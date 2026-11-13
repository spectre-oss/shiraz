# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import io
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

from shiraz.apk import FormatError  # noqa: E402
from shiraz.apk_signer import SCHEME_V2, SCHEME_V3, certificate_digests  # noqa: E402
from shiraz.fdroid import releases  # noqa: E402


def tlv(tag, content):
    length = len(content)
    if length < 0x80:
        head = bytes([length])
    else:
        raw = length.to_bytes((length.bit_length() + 7) // 8, 'big')
        head = bytes([0x80 | len(raw)]) + raw
    return bytes([tag]) + head + content


SIGNED_DATA_OID = bytes.fromhex('06092a864886f70d010702')
DATA_OID = bytes.fromhex('06092a864886f70d010701')


def certificate(name):
    """A DER SEQUENCE standing in for an X.509 certificate."""
    return tlv(0x30, tlv(0x0C, name.encode()))


def pkcs7(cert):
    inner = (
        tlv(0x02, b'\x01') + tlv(0x31, b'') + tlv(0x30, DATA_OID) + tlv(0xA0, cert) + tlv(0x31, b'')
    )
    return tlv(0x30, SIGNED_DATA_OID + tlv(0xA0, tlv(0x30, inner)))


def prefixed(data):
    return struct.pack('<I', len(data)) + data


def scheme_block(certs):
    signers = b''
    for cert in certs:
        signed_data = prefixed(b'') + prefixed(prefixed(cert)) + prefixed(b'')
        signers += prefixed(prefixed(signed_data) + prefixed(b'') + prefixed(b''))
    return prefixed(signers)


def zip_bytes(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return out.getvalue()


def with_signing_block(data, pairs):
    """Insert an APK Signing Block before the central directory."""
    eocd = data.rfind(b'PK\x05\x06')
    (central,) = struct.unpack_from('<I', data, eocd + 16)
    body = b''.join(
        struct.pack('<Q', len(value) + 4) + struct.pack('<I', ident) + value
        for ident, value in pairs
    )
    size = len(body) + 24
    block = struct.pack('<Q', size) + body + struct.pack('<Q', size) + b'APK Sig Block 42'
    patched = data[:central] + block + data[central:]
    at = eocd + len(block)
    return patched[: at + 16] + struct.pack('<I', central + len(block)) + patched[at + 20 :]


class ApkSignerTests(unittest.TestCase):
    def write(self, data):
        handle = tempfile.NamedTemporaryFile(suffix='.apk', delete=False)
        handle.write(data)
        handle.close()
        self.addCleanup(Path(handle.name).unlink)
        return handle.name

    def test_v1_signature_block_certificate(self):
        cert = certificate('v1')
        apk = self.write(zip_bytes({'AndroidManifest.xml': b'x', 'META-INF/CERT.RSA': pkcs7(cert)}))
        self.assertEqual(certificate_digests(apk), [hashlib.sha256(cert).hexdigest()])

    def test_v3_preferred_over_v2_and_v1(self):
        v1, v2, v3 = certificate('one'), certificate('two'), certificate('three')
        data = zip_bytes({'AndroidManifest.xml': b'x', 'META-INF/CERT.RSA': pkcs7(v1)})
        apk = self.write(
            with_signing_block(
                data, [(SCHEME_V2, scheme_block([v2])), (SCHEME_V3, scheme_block([v3]))]
            )
        )
        self.assertEqual(certificate_digests(apk), [hashlib.sha256(v3).hexdigest()])
        apk = self.write(with_signing_block(data, [(SCHEME_V2, scheme_block([v2]))]))
        self.assertEqual(certificate_digests(apk), [hashlib.sha256(v2).hexdigest()])

    def test_unsigned_and_truncated_blocks_are_rejected(self):
        with self.assertRaises(FormatError):
            certificate_digests(self.write(zip_bytes({'AndroidManifest.xml': b'x'})))
        data = zip_bytes({'AndroidManifest.xml': b'x'})
        with self.assertRaises(FormatError):
            certificate_digests(
                self.write(with_signing_block(data, [(SCHEME_V2, struct.pack('<I', 99))]))
            )

    def test_releases_keep_only_hashed_signed_versions(self):
        good = 'a' * 64
        packages = [
            {'versionCode': 2, 'hashType': 'sha256', 'hash': good, 'signer': 'b' * 64},
            {'versionCode': 3, 'hashType': 'sha256', 'hash': good, 'signer': 'not-hex'},
            {'versionCode': 1, 'hashType': 'md5', 'hash': good, 'signer': 'b' * 64},
            {'versionCode': 4, 'hashType': 'sha256', 'hash': 'c' * 64, 'signer': 'b' * 64},
        ]
        self.assertEqual(
            releases(packages),
            [
                {'versionCode': 4, 'sha256': 'c' * 64, 'signer': 'b' * 64},
                {'versionCode': 2, 'sha256': good, 'signer': 'b' * 64},
            ],
        )


if __name__ == '__main__':
    unittest.main()
