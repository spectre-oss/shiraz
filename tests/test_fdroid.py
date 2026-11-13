# SPDX-License-Identifier: GPL-3.0-only
"""F-Droid index verification, catalog selection and verified fetch, offline.

A throwaway RSA key signs a synthetic index-v1.jar the way jarsigner does
(PKCS#7 SignedData with signed attributes over the .SF file).
"""
import base64
import hashlib
import io
import json
import random
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from shiraz import fdroid
from shiraz.apk import FormatError


def tlv(tag, content):
    n = len(content)
    if n < 0x80:
        length = bytes([n])
    else:
        raw = n.to_bytes((n.bit_length() + 7) // 8, 'big')
        length = bytes([0x80 | len(raw)]) + raw
    return bytes([tag]) + length + content


def seq(*items):
    return tlv(0x30, b''.join(items))


def der_int(value):
    raw = value.to_bytes(value.bit_length() // 8 + 1, 'big', signed=True)
    return tlv(0x02, raw)


def der_oid(text):
    parts = [int(p) for p in text.split('.')]
    body = bytes([40 * parts[0] + parts[1]])
    for part in parts[2:]:
        chunk = [part & 0x7f]
        part >>= 7
        while part:
            chunk.append(0x80 | part & 0x7f)
            part >>= 7
        body += bytes(reversed(chunk))
    return tlv(0x06, body)


NULL = b'\x05\x00'


def prime(bits, rng):
    while True:
        candidate = rng.getrandbits(bits) | 1 << (bits - 1) | 1
        if all(candidate % p for p in (3, 5, 7, 11, 13, 17, 19, 23, 29, 31)) and probable_prime(candidate, rng):
            return candidate


def probable_prime(n, rng):
    d, r = n - 1, 0
    while d % 2 == 0:
        d //= 2
        r += 1
    for _ in range(16):
        x = pow(rng.randrange(2, n - 2), d, n)
        if x in (1, n - 1):
            continue
        for _ in range(r - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


RNG = random.Random(20261111)
E = 65537
while True:
    P, Q = prime(1024, RNG), prime(1024, RNG)
    if P != Q and (P - 1) % E and (Q - 1) % E:
        break
N = P * Q
D = pow(E, -1, (P - 1) * (Q - 1))


def sign(message, name='sha256'):
    _, prefix, _ = next(v for v in fdroid.DIGESTS.values() if v[0] == name)
    k = (N.bit_length() + 7) // 8
    tail = prefix + hashlib.new(name, message).digest()
    encoded = b'\x00\x01' + b'\xff' * (k - 3 - len(tail)) + b'\x00' + tail
    return pow(int.from_bytes(encoded, 'big'), D, N).to_bytes(k, 'big')


CERT = seq(
    seq(tlv(0xa0, der_int(2)), der_int(7), seq(der_oid('1.2.840.113549.1.1.11'), NULL), seq(), seq(), seq(),
        seq(seq(der_oid(fdroid.OID_RSA), NULL), tlv(0x03, b'\x00' + seq(der_int(N), der_int(E))))),
    seq(der_oid('1.2.840.113549.1.1.11'), NULL), tlv(0x03, b'\x00\x01'))
FINGERPRINT = hashlib.sha256(CERT).hexdigest()


def pkcs7(signed, name='sha256', corrupt=False):
    digest_oid = next(k for k, v in fdroid.DIGESTS.items() if v[0] == name)
    algorithm = seq(der_oid(digest_oid), NULL)
    attributes = seq(der_oid(fdroid.OID_MESSAGE_DIGEST), tlv(0x31, tlv(0x04, hashlib.new(name, signed).digest())))
    signature = bytearray(sign(tlv(0x31, attributes), name))
    if corrupt:
        signature[-1] ^= 1
    signer = seq(der_int(1), seq(seq(), der_int(7)), algorithm, tlv(0xa0, attributes),
                 seq(der_oid(fdroid.OID_RSA), NULL), tlv(0x04, bytes(signature)))
    signed_data = seq(der_int(1), tlv(0x31, algorithm), seq(der_oid('1.2.840.113549.1.7.1')),
                      tlv(0xa0, CERT), tlv(0x31, signer))
    return seq(der_oid(fdroid.OID_SIGNED_DATA), tlv(0xa0, signed_data))


def index_jar(index, name='sha256', corrupt=False, tamper=None):
    label = next(v[2] for v in fdroid.DIGESTS.values() if v[0] == name)
    b64 = lambda data: base64.b64encode(hashlib.new(name, data).digest()).decode()
    manifest = f'Manifest-Version: 1.0\r\n\r\nName: index-v1.json\r\n{label}-Digest: {b64(index)}\r\n\r\n'.encode()
    sf = (f'Signature-Version: 1.0\r\n{label}-Digest-Manifest: {b64(manifest)}\r\n\r\n'
          f'Name: index-v1.json\r\n{label}-Digest: x\r\n\r\n').encode()
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', manifest)
        archive.writestr('META-INF/TEST.SF', sf)
        archive.writestr('META-INF/TEST.RSA', pkcs7(sf, name, corrupt))
        archive.writestr('index-v1.json', tamper if tamper is not None else index)
    return out.getvalue()


def package(code, **extra):
    return {'apkName': f'org.example.app_{code}.apk', 'hash': hashlib.sha256(str(code).encode()).hexdigest(),
            'hashType': 'sha256', 'size': 3, 'versionCode': code, 'versionName': str(code), 'minSdkVersion': 21, **extra}


INDEX = json.dumps({
    'repo': {'name': 'Test', 'timestamp': 1, 'webBaseUrl': 'https://f-droid.org/packages'},
    'apps': [
        {'packageName': 'org.example.app', 'suggestedVersionCode': '20', 'localized': {'en-US': {'name': 'Example', 'summary': 'S', 'icon': 'i.png'}}},
        {'packageName': 'org.example.x86', 'name': 'X86', 'icon': 'x.png'},
        {'packageName': 'org.example.new', 'name': 'New'},
        {'packageName': '../evil', 'name': 'Evil'},
    ],
    'packages': {
        'org.example.app': [package(30), package(20), package(10)],
        'org.example.x86': [package(5, nativecode=['x86_64'])],
        'org.example.new': [package(9, minSdkVersion=33)],
    },
}).encode()


class IndexVerification(unittest.TestCase):
    def test_signed_index_verifies(self):
        for name in ('sha256', 'sha1'):
            self.assertEqual(fdroid.verify_index_jar(index_jar(INDEX, name), FINGERPRINT), INDEX)

    def test_rejects_wrong_pin_bad_signature_and_tampered_index(self):
        with self.assertRaisesRegex(FormatError, 'pinned fingerprint'):
            fdroid.verify_index_jar(index_jar(INDEX))
        with self.assertRaisesRegex(FormatError, 'does not verify'):
            fdroid.verify_index_jar(index_jar(INDEX, corrupt=True), FINGERPRINT)
        with self.assertRaisesRegex(FormatError, 'signed digest'):
            fdroid.verify_index_jar(index_jar(INDEX, tamper=INDEX.replace(b'Example', b'Exampl3')), FINGERPRINT)

    def test_rejects_malformed_archives(self):
        with self.assertRaises(FormatError):
            fdroid.verify_index_jar(b'not a zip', FINGERPRINT)
        with self.assertRaises(FormatError):
            fdroid.der(b'\x30\x84\xff\xff\xff\xff')


class Catalog(unittest.TestCase):
    def test_selection_and_reasons(self):
        apps = {a['packageName']: a for a in fdroid.build_catalog(INDEX)['apps']}
        self.assertNotIn('../evil', apps)
        app = apps['org.example.app']
        self.assertEqual((app['name'], app['versionCode'], app['compatible']), ('Example', 20, True))
        self.assertEqual(app['iconURL'], 'https://f-droid.org/repo/org.example.app/en-US/i.png')
        self.assertFalse(apps['org.example.x86']['compatible'])
        self.assertIn('x86_64', apps['org.example.x86']['reason'])
        self.assertIn('API 33', apps['org.example.new']['reason'])

    def test_details_are_plain_and_bounded(self):
        index = json.loads(INDEX)
        index['apps'][0].update(
            authorName='Ada', sourceCode='https://example.org/src', antiFeatures=['Ads', 3]
        )
        index['apps'][0]['localized']['en-US'].update(
            description='<p>One &amp; two</p><ul><li>A</li></ul><script>x</script>',
            phoneScreenshots=['1.png', '../escape.png', '.hidden.png'],
        )
        index['apps'][1]['sourceCode'] = 'http://insecure.example/src'
        apps = {
            a['packageName']: a for a in fdroid.build_catalog(json.dumps(index).encode())['apps']
        }
        app = apps['org.example.app']
        self.assertEqual(app['description'], 'One & two\n\n• A')
        self.assertEqual(
            (app['authorName'], app['sourceCode'], app['antiFeatures']),
            ('Ada', 'https://example.org/src', ['Ads']),
        )
        self.assertEqual(
            app['screenshots'],
            ['https://f-droid.org/repo/org.example.app/en-US/phoneScreenshots/1.png'],
        )
        self.assertIsNone(apps['org.example.x86']['sourceCode'])
        self.assertTrue(fdroid.plain_text('x' * 5000).endswith('…'))


class Fetch(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.cache = Path(self.temp.name)
        (self.cache / 'catalog.json').write_text(json.dumps(fdroid.build_catalog(INDEX)))

    def tearDown(self):
        self.temp.cleanup()

    def fake_download(self, payload):
        def download(url, limit, destination=None, expected_size=None):
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.parent / '.download-test'
            temporary.write_bytes(payload)
            return str(temporary), hashlib.sha256(payload).hexdigest()
        return download

    def test_fetch_checks_hash_and_compatibility(self):
        output = self.cache / 'apks'
        with mock.patch.object(fdroid, 'download', self.fake_download(b'20')):
            result = fdroid.fetch(self.cache, 'org.example.app', output)
        self.assertEqual(Path(result['path']).read_bytes(), b'20')
        with mock.patch.object(fdroid, 'download', self.fake_download(b'bad')):
            with self.assertRaisesRegex(FormatError, 'does not match the signed index'):
                fdroid.fetch(self.cache, 'org.example.app', self.cache / 'other')
        self.assertEqual([p.name for p in (self.cache / 'other').iterdir()], [])
        with self.assertRaisesRegex(FormatError, 'cannot run in Shiraz'):
            fdroid.fetch(self.cache, 'org.example.x86', output)
        with self.assertRaisesRegex(FormatError, 'Invalid package'):
            fdroid.fetch(self.cache, '../evil', output)

    def test_downloads_require_https(self):
        with self.assertRaisesRegex(FormatError, 'HTTPS'):
            fdroid.download('http://f-droid.org/repo/x.apk', 10)


if __name__ == '__main__':
    unittest.main()
