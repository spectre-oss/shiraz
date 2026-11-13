# SPDX-License-Identifier: GPL-3.0-only
"""F-Droid repository client: signed index, catalog, verified APK download.

The repository index (index-v1.jar) is a signed JAR. It is trusted only when
its signing certificate matches the pinned repository fingerprint, the PKCS#7
signature over the .SF file verifies (RSA PKCS#1 v1.5), the .SF digest
matches MANIFEST.MF and the manifest digest matches index-v1.json.
f-droid.org signs index-v1.jar with SHA-1 (as F-Droid's own client accepts);
SHA-256 is used when the JAR offers it. The SHA-256-signed index-v2 is ~72 MB
uncompressed, so it is not used yet.
APKs are trusted only when their SHA-256 equals the hash in that index.
Everything is bounded: archive entries, decompressed sizes and downloads.
"""
import base64
import hashlib
import io
import json
import os
import re
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

from .apk import FormatError

REPO = 'https://f-droid.org/repo'
# SHA-256 of the f-droid.org repository signing certificate (DER).
REPO_FINGERPRINT = '43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab'
INDEX_NAME = 'index-v1.json'
MAX_INDEX_JAR = 64 * 1024 * 1024
MAX_INDEX_JSON = 256 * 1024 * 1024
MAX_SIGNATURE_FILE = 64 * 1024
MAX_APK = 512 * 1024 * 1024
API_LEVEL = 27  # Shiraz's current Android API profile (runtime.py rejects newer minSdk).
HOST_ABI = 'arm64-v8a'
USER_AGENT = 'Shiraz-FDroid/1'
PACKAGE = re.compile(r'[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+')
DIGESTS = {  # digest OID -> (hashlib name, PKCS#1 DigestInfo prefix, JAR attribute prefix)
    '1.3.14.3.2.26': ('sha1', bytes.fromhex('3021300906052b0e03021a05000414'), 'SHA1'),
    '2.16.840.1.101.3.4.2.1': ('sha256', bytes.fromhex('3031300d060960864801650304020105000420'), 'SHA-256'),
}
OID_SIGNED_DATA = '1.2.840.113549.1.7.2'
OID_MESSAGE_DIGEST = '1.2.840.113549.1.9.4'
OID_RSA = '1.2.840.113549.1.1.1'
OID_RSA_SIGNATURES = {'1.2.840.113549.1.1.1', '1.2.840.113549.1.1.5', '1.2.840.113549.1.1.11'}


class Der:
    """One DER element: tag byte, raw encoding and content bounds."""

    def __init__(self, data, start):
        if start + 2 > len(data):
            raise FormatError('Truncated DER element')
        self.data, self.start, self.tag = data, start, data[start]
        if self.tag & 0x1f == 0x1f:
            raise FormatError('Unsupported DER high tag number')
        length, at = data[start + 1], start + 2
        if length & 0x80:
            count = length & 0x7f
            if count == 0 or count > 4 or at + count > len(data):
                raise FormatError('Unsupported DER length')
            length = int.from_bytes(data[at:at + count], 'big')
            at += count
        self.content, self.end = at, at + length
        if self.end > len(data):
            raise FormatError('DER element exceeds its container')

    @property
    def raw(self):
        return self.data[self.start:self.end]

    @property
    def value(self):
        return self.data[self.content:self.end]

    def children(self):
        if not self.tag & 0x20:
            raise FormatError('DER element is not constructed')
        result, at = [], self.content
        while at < self.end:
            child = Der(self.data, at)
            if child.end > self.end:
                raise FormatError('DER child exceeds its parent')
            result.append(child)
            at = child.end
        return result

    def expect(self, tag):
        if self.tag != tag:
            raise FormatError(f'Unexpected DER tag 0x{self.tag:02x}, expected 0x{tag:02x}')
        return self


def der(data):
    element = Der(data, 0)
    if element.end != len(data):
        raise FormatError('Trailing bytes after DER element')
    return element


def oid(element):
    raw = element.expect(0x06).value
    if not raw:
        raise FormatError('Empty object identifier')
    parts, value = [], 0
    for byte in raw:
        value = value << 7 | byte & 0x7f
        if not byte & 0x80:
            parts.append(value)
            value = 0
    first = min(parts[0] // 40, 2)
    return '.'.join(map(str, [first, parts[0] - 40 * first, *parts[1:]]))


def integer(element):
    return int.from_bytes(element.expect(0x02).value, 'big', signed=True)


def rsa_public_key(certificate):
    """Modulus and exponent from an X.509 certificate's SubjectPublicKeyInfo."""
    tbs = certificate.expect(0x30).children()[0].expect(0x30).children()
    if tbs and tbs[0].tag == 0xa0:
        tbs = tbs[1:]
    if len(tbs) < 6:
        raise FormatError('Malformed certificate')
    algorithm, key = tbs[5].expect(0x30).children()[:2]
    if oid(algorithm.expect(0x30).children()[0]) != OID_RSA:
        raise FormatError('Repository certificate key is not RSA')
    bits = key.expect(0x03).value
    if not bits or bits[0] != 0:
        raise FormatError('Malformed public key bit string')
    modulus, exponent = der(bits[1:]).expect(0x30).children()[:2]
    n, e = integer(modulus), integer(exponent)
    if n <= 0 or e <= 1 or n.bit_length() < 2048:
        raise FormatError('Repository RSA key is too small or invalid')
    return n, e


def verify_rsa(n, e, signature, prefix, digest):
    k = (n.bit_length() + 7) // 8
    if len(signature) != k:
        raise FormatError('RSA signature length does not match the key')
    s = int.from_bytes(signature, 'big')
    if s >= n:
        raise FormatError('RSA signature out of range')
    encoded = pow(s, e, n).to_bytes(k, 'big')
    tail = prefix + digest
    expected = b'\x00\x01' + b'\xff' * (k - 3 - len(tail)) + b'\x00' + tail
    if k - 3 - len(tail) < 8 or encoded != expected:
        raise FormatError('Repository index signature does not verify')


def verify_pkcs7(block, signed, fingerprint=REPO_FINGERPRINT):
    """Verify a detached JAR PKCS#7 signature over `signed` (the .SF file)."""
    content = der(block).expect(0x30).children()
    if len(content) != 2 or oid(content[0]) != OID_SIGNED_DATA:
        raise FormatError('Signature block is not PKCS#7 SignedData')
    fields = content[1].expect(0xa0).children()[0].expect(0x30).children()
    certificates = next((f for f in fields if f.tag == 0xa0), None)
    signers = fields[-1].expect(0x31).children()
    if certificates is None or len(signers) != 1:
        raise FormatError('Signature block needs one certificate set and one signer')
    certificate = certificates.children()[0]
    actual = hashlib.sha256(certificate.raw).hexdigest()
    if actual != fingerprint:
        raise FormatError(f'Repository certificate {actual} does not match the pinned fingerprint')
    n, e = rsa_public_key(certificate)
    signer = signers[0].expect(0x30).children()
    if len(signer) < 5:
        raise FormatError('Malformed PKCS#7 signer')
    digest_oid = oid(signer[2].expect(0x30).children()[0])
    if digest_oid not in DIGESTS:
        raise FormatError('Repository index signer digest is neither SHA-1 nor SHA-256')
    name, prefix, _ = DIGESTS[digest_oid]
    at = 3
    attributes = None
    if signer[at].tag == 0xa0:
        attributes = signer[at]
        at += 1
    algorithm = oid(signer[at].expect(0x30).children()[0])
    if algorithm not in OID_RSA_SIGNATURES:
        raise FormatError('Repository index signature algorithm is not RSA')
    signature = signer[at + 1].expect(0x04).value
    content_digest = hashlib.new(name, signed).digest()
    if attributes is None:
        verify_rsa(n, e, signature, prefix, content_digest)
        return actual
    digests = []
    for attribute in attributes.children():
        kind, values = attribute.expect(0x30).children()[:2]
        if oid(kind) == OID_MESSAGE_DIGEST:
            digests.extend(v.expect(0x04).value for v in values.expect(0x31).children())
    if digests != [content_digest]:
        raise FormatError('Signed attributes do not match the signature file')
    # The signature covers the attributes re-encoded as a SET (tag 0x31).
    verify_rsa(n, e, signature, prefix, hashlib.new(name, b'\x31' + attributes.raw[1:]).digest())
    return actual


def manifest_sections(text):
    """JAR manifest sections as dicts; continuation lines start with one space."""
    lines = []
    for line in text.replace('\r\n', '\n').split('\n'):
        if line.startswith(' ') and lines:
            lines[-1] += line[1:]
        else:
            lines.append(line)
    sections, current = [], {}
    for line in lines:
        if not line:
            if current:
                sections.append(current)
            current = {}
            continue
        key, sep, value = line.partition(': ')
        if not sep:
            raise FormatError('Malformed JAR manifest line')
        current[key] = value
    if current:
        sections.append(current)
    return sections


def b64(name, data):
    return base64.b64encode(hashlib.new(name, data).digest()).decode()


def read_bounded(archive, info, limit):
    if info.file_size > limit:
        raise FormatError(f'{info.filename} exceeds {limit} bytes')
    with archive.open(info) as stream:
        data = stream.read(limit + 1)
    if len(data) > limit or len(data) != info.file_size:
        raise FormatError(f'{info.filename} size does not match its directory entry')
    return data


def verify_index_jar(data, fingerprint=REPO_FINGERPRINT):
    """Return the verified index-v1.json bytes from an index-v1.jar."""
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as exc:
        raise FormatError(f'Repository index is not a ZIP archive: {exc}') from exc
    with archive:
        infos = archive.infolist()
        if len(infos) > 16 or len({i.filename for i in infos}) != len(infos):
            raise FormatError('Unexpected repository index archive layout')
        names = {i.filename: i for i in infos}
        signature_files = [n for n in names if re.fullmatch(r'META-INF/[A-Za-z0-9_-]+\.SF', n)]
        if len(signature_files) != 1 or INDEX_NAME not in names or 'META-INF/MANIFEST.MF' not in names:
            raise FormatError('Repository index archive lacks one signature, manifest or index')
        base = signature_files[0][:-3]
        block_name = base + '.RSA'
        if block_name not in names:
            raise FormatError('Repository index signature is not RSA')
        signed = read_bounded(archive, names[signature_files[0]], MAX_SIGNATURE_FILE)
        block = read_bounded(archive, names[block_name], MAX_SIGNATURE_FILE)
        manifest = read_bounded(archive, names['META-INF/MANIFEST.MF'], MAX_SIGNATURE_FILE)
        verify_pkcs7(block, signed, fingerprint)
        sf = manifest_sections(signed.decode('utf-8'))
        main = sf[0] if sf else {}
        # Strongest digest the signature file offers.
        name, label = next(((n, l) for n, _, l in reversed(DIGESTS.values()) if l + '-Digest-Manifest' in main), (None, None))
        if name is None or main[label + '-Digest-Manifest'] != b64(name, manifest):
            raise FormatError('Signature file does not match the JAR manifest')
        entry = [s for s in manifest_sections(manifest.decode('utf-8')) if s.get('Name') == INDEX_NAME]
        if len(entry) != 1 or label + '-Digest' not in entry[0]:
            raise FormatError('JAR manifest lacks the index digest')
        index = read_bounded(archive, names[INDEX_NAME], MAX_INDEX_JSON)
        if entry[0][label + '-Digest'] != b64(name, index):
            raise FormatError('Repository index does not match its signed digest')
        return index


def _localized(app, key):
    localized = app.get('localized') or {}
    for locale in ('en-US', 'en', 'en-GB', *sorted(localized)):
        value = (localized.get(locale) or {}).get(key)
        if value:
            return value, locale
    return None, None


def plain_text(value, limit=4000):
    """Catalog descriptions are HTML fragments; keep readable text only."""
    if not isinstance(value, str):
        return ''
    text = re.sub(r'(?is)<(script|style)\b.{0,65536}?</\1\s*>', '', value)
    text = re.sub(r'(?i)<br\s*/?>|</p>|</li>', '\n', text)
    text = re.sub(r'(?i)<li[^>]*>', '\n• ', text)
    text = re.sub(r'<[^>]{0,512}>', '', text)
    for entity, char in (
        ('&lt;', '<'),
        ('&gt;', '>'),
        ('&quot;', '"'),
        ('&#39;', "'"),
        ('&nbsp;', ' '),
        ('&amp;', '&'),
    ):
        text = text.replace(entity, char)
    text = re.sub(r'\n[ \t]*\n[\s]*', '\n\n', text).strip()
    return text[:limit].rstrip() + ('…' if len(text) > limit else '')


def https_url(value):
    return (
        value
        if isinstance(value, str) and value.startswith('https://') and len(value) <= 2048
        else None
    )


def choose_package(app, packages):
    """Best APK for this host: suggested or newer-compatible version, else a reason."""
    suggested = app.get('suggestedVersionCode')
    try:
        ceiling = int(suggested) if suggested is not None else None
    except ValueError:
        ceiling = None
    candidates = sorted(packages, key=lambda p: p.get('versionCode', 0), reverse=True)
    if ceiling is not None and any(p.get('versionCode', 0) <= ceiling for p in candidates):
        candidates = [p for p in candidates if p.get('versionCode', 0) <= ceiling]
    reason = 'No APK listed'
    for package in candidates:
        native = package.get('nativecode') or []
        if native and HOST_ABI not in native:
            reason = 'Native code only for ' + ', '.join(native)
            continue
        if int(package.get('minSdkVersion', 1) or 1) > API_LEVEL:
            reason = f"Requires Android API {package.get('minSdkVersion')}"
            continue
        if package.get('hashType') != 'sha256' or not re.fullmatch(r'[0-9a-f]{64}', package.get('hash', '')):
            reason = 'APK has no SHA-256 hash'
            continue
        return package, None
    return (candidates[0] if candidates else None), reason


HEX256 = re.compile(r'[0-9a-f]{64}')
MAX_RELEASES = 16


def releases(packages):
    """Newest listed APKs with their SHA-256 and signer, for checking updates.

    An APK whose hash is in this list is the repository's own build; its
    `signer` is the SHA-256 of the signing certificate (as apk_signer reports).
    """
    result = []
    for package in sorted(packages, key=lambda p: p.get('versionCode', 0), reverse=True):
        code, digest, signer = (
            package.get('versionCode'),
            package.get('hash'),
            package.get('signer'),
        )
        if package.get('hashType') != 'sha256' or not isinstance(code, int):
            continue
        if not isinstance(digest, str) or not HEX256.fullmatch(digest):
            continue
        if not isinstance(signer, str) or not HEX256.fullmatch(signer):
            continue
        result.append({'versionCode': code, 'sha256': digest, 'signer': signer})
        if len(result) == MAX_RELEASES:
            break
    return result


def build_catalog(index, address=REPO):
    data = json.loads(index)
    repo = data.get('repo') or {}
    apps = []
    for app in data.get('apps') or []:
        name = app.get('packageName')
        if not isinstance(name, str) or not PACKAGE.fullmatch(name):
            continue
        listed = (data.get('packages') or {}).get(name) or []
        package, reason = choose_package(app, listed)
        if package is None:
            continue
        title = app.get('name') or _localized(app, 'name')[0] or name
        summary = app.get('summary') or _localized(app, 'summary')[0] or ''
        icon, locale = _localized(app, 'icon')
        if icon:
            icon_url = f'{address}/{name}/{locale}/{icon}'
        elif app.get('icon'):
            icon_url = f"{address}/icons-640/{app['icon']}"
        else:
            icon_url = None
        description, _ = _localized(app, 'description')
        shots, shot_locale = _localized(app, 'phoneScreenshots')
        screenshots = [
            f'{address}/{name}/{shot_locale}/phoneScreenshots/{shot}'
            for shot in (shots or [])[:6]
            if isinstance(shot, str) and '/' not in shot and not shot.startswith('.')
        ]
        apps.append(
            {
                'packageName': name,
                'name': title,
                'summary': summary,
                'description': plain_text(description or app.get('description')),
                'authorName': app.get('authorName')
                if isinstance(app.get('authorName'), str)
                else None,
                'sourceCode': https_url(app.get('sourceCode')),
                'antiFeatures': [a for a in app.get('antiFeatures') or [] if isinstance(a, str)],
                'screenshots': screenshots,
                'license': app.get('license'),
                'categories': app.get('categories') or [],
                'iconURL': icon_url,
                'lastUpdated': app.get('lastUpdated'),
                'added': app.get('added'),
                'versionName': package.get('versionName'),
                'versionCode': package.get('versionCode'),
                'apkName': package.get('apkName'),
                'sha256': package.get('hash'),
                'size': package.get('size'),
                'nativecode': package.get('nativecode') or [],
                'minSdk': package.get('minSdkVersion'),
                'compatible': reason is None,
                'reason': reason,
                'webURL': f"{repo.get('webBaseUrl', 'https://f-droid.org/packages')}/{name}/",
                'releases': releases(listed),
            }
        )
    apps.sort(key=lambda a: (a['name'].casefold(), a['packageName']))
    return {'repo': {'name': repo.get('name'), 'address': address, 'timestamp': repo.get('timestamp'),
                     'fingerprint': REPO_FINGERPRINT}, 'apps': apps}


def download(url, limit, destination=None, expected_size=None):
    """Bounded HTTPS download into memory, or into `destination` atomically."""
    if not url.startswith('https://'):
        raise FormatError('F-Droid downloads must use HTTPS')
    request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
    try:
        response = urllib.request.urlopen(request, timeout=60)
    except urllib.error.URLError as exc:
        raise FormatError(f'Cannot download {url}: {exc.reason}') from exc
    with response:
        declared = response.headers.get('Content-Length')
        if declared is not None and int(declared) > limit:
            raise FormatError(f'{url} exceeds {limit} bytes')
        if destination is None:
            data = response.read(limit + 1)
            if len(data) > limit:
                raise FormatError(f'{url} exceeds {limit} bytes')
            return data
        destination.parent.mkdir(parents=True, exist_ok=True)
        handle, temporary = tempfile.mkstemp(dir=destination.parent, prefix='.download-')
        digest, total = hashlib.sha256(), 0
        try:
            with os.fdopen(handle, 'wb') as out:
                while chunk := response.read(1 << 20):
                    total += len(chunk)
                    if total > limit or (expected_size is not None and total > expected_size):
                        raise FormatError(f'{url} is larger than the index states')
                    digest.update(chunk)
                    out.write(chunk)
            if expected_size is not None and total != expected_size:
                raise FormatError(f'{url} size {total} does not match the index ({expected_size})')
            return temporary, digest.hexdigest()
        except BaseException:
            Path(temporary).unlink(missing_ok=True)
            raise


def sync(cache, max_age=0):
    """Fetch and verify the index; write catalog.json; return the catalog."""
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / 'catalog.json'
    if max_age and target.is_file() and time.time() - target.stat().st_mtime < max_age:
        return json.loads(target.read_text())
    index = verify_index_jar(download(f'{REPO}/index-v1.jar', MAX_INDEX_JAR))
    catalog = build_catalog(index)
    temporary = target.with_suffix('.tmp')
    temporary.write_text(json.dumps(catalog, ensure_ascii=False))
    temporary.replace(target)
    return catalog


def fetch(cache, package_name, output):
    """Download the catalog's APK for `package_name` into `output`, SHA-256 checked."""
    if not PACKAGE.fullmatch(package_name):
        raise FormatError('Invalid package name')
    catalog_file = cache / 'catalog.json'
    if not catalog_file.is_file():
        raise FormatError('No F-Droid catalog; run shiraz fdroid sync first')
    entry = next((a for a in json.loads(catalog_file.read_text())['apps'] if a['packageName'] == package_name), None)
    if entry is None:
        raise FormatError(f'{package_name} is not in the F-Droid catalog')
    if not entry['compatible']:
        raise FormatError(f"{package_name} cannot run in Shiraz: {entry['reason']}")
    apk = entry['apkName']
    if not isinstance(apk, str) or '/' in apk or not apk.endswith('.apk'):
        raise FormatError('Catalog APK name is unsafe')
    size = entry.get('size')
    if not isinstance(size, int) or not 0 < size <= MAX_APK:
        raise FormatError('Catalog APK size is missing or too large')
    destination = output / apk
    temporary, digest = download(f'{REPO}/{apk}', MAX_APK, destination, size)
    if digest != entry['sha256']:
        Path(temporary).unlink(missing_ok=True)
        raise FormatError(f'{apk} SHA-256 {digest} does not match the signed index')
    os.replace(temporary, destination)
    return {'packageName': package_name, 'path': str(destination), 'sha256': digest, 'size': size,
            'versionName': entry.get('versionName')}
