# SPDX-License-Identifier: GPL-3.0-only
"""Signing certificates named by an APK, as SHA-256 digests of their DER.

This reads certificates; it does not verify signatures. Use it only for an
APK that is already trusted (an installed library copy, or one whose SHA-256
was checked against a signed repository index), for example to tell whether
an update comes from the same signer. F-Droid's index `signer` field uses the
same digest.

Order of preference: APK Signature Scheme v3, then v2, then the v1 JAR
signature block (META-INF/*.RSA, *.DSA or *.EC).
"""

import hashlib
import struct
import zipfile

from .apk import FormatError
from .fdroid import OID_SIGNED_DATA, der, oid

BLOCK_MAGIC = b'APK Sig Block 42'
SCHEME_V2 = 0x7109871A
SCHEME_V3 = 0xF05368C0
MAX_EOCD_SEARCH = 0xFFFF + 22
MAX_SIGNING_BLOCK = 64 * 1024 * 1024
MAX_V1_BLOCK = 1024 * 1024
MAX_SIGNERS = 16
MAX_CERTIFICATES = 16


def _prefixed(data, at):
    """One uint32 length-prefixed item: (bytes, next offset)."""
    if at + 4 > len(data):
        raise FormatError('Truncated APK signing block item')
    (length,) = struct.unpack_from('<I', data, at)
    end = at + 4 + length
    if end > len(data):
        raise FormatError('APK signing block item exceeds its container')
    return data[at + 4 : end], end


def _sequence(data, limit):
    items, at = [], 0
    while at < len(data):
        item, at = _prefixed(data, at)
        items.append(item)
        if len(items) > limit:
            raise FormatError('Too many APK signing block items')
    return items


def signing_block(file):
    """ID -> value pairs of the APK Signing Block, or {} when there is none."""
    file.seek(0, 2)
    size = file.tell()
    tail_size = min(size, MAX_EOCD_SEARCH)
    file.seek(size - tail_size)
    tail = file.read(tail_size)
    eocd = tail.rfind(b'PK\x05\x06')
    if eocd < 0 or eocd + 22 > len(tail):
        raise FormatError('APK has no ZIP end of central directory')
    (central,) = struct.unpack_from('<I', tail, eocd + 16)
    if central < 32 or central > size:
        return {}
    file.seek(central - 24)
    footer = file.read(24)
    if footer[8:] != BLOCK_MAGIC:
        return {}
    (block_size,) = struct.unpack_from('<Q', footer, 0)
    if block_size < 24 or block_size > MAX_SIGNING_BLOCK or block_size + 8 > central:
        raise FormatError('APK signing block size is invalid')
    file.seek(central - block_size - 8)
    block = file.read(block_size + 8)
    if struct.unpack_from('<Q', block, 0)[0] != block_size:
        raise FormatError('APK signing block sizes disagree')
    pairs, at, end = {}, 8, len(block) - 24
    while at < end:
        if at + 12 > end:
            raise FormatError('Truncated APK signing block pair')
        (length,) = struct.unpack_from('<Q', block, at)
        if length < 4 or at + 8 + length > end:
            raise FormatError('APK signing block pair exceeds the block')
        (ident,) = struct.unpack_from('<I', block, at + 8)
        pairs.setdefault(ident, block[at + 12 : at + 8 + length])
        at += 8 + length
    return pairs


def scheme_certificates(value):
    """First certificate of each signer in a v2 or v3 signature scheme block."""
    signers, _ = _prefixed(value, 0)
    digests = []
    for signer in _sequence(signers, MAX_SIGNERS):
        signed_data, _ = _prefixed(signer, 0)
        _, at = _prefixed(signed_data, 0)  # content digests
        certificates, _ = _prefixed(signed_data, at)
        chain = _sequence(certificates, MAX_CERTIFICATES)
        if not chain:
            raise FormatError('APK signer has no certificate')
        digests.append(hashlib.sha256(der(chain[0]).raw).hexdigest())
    if not digests:
        raise FormatError('APK signature scheme block has no signers')
    return digests


def v1_certificates(archive):
    names = sorted(
        n
        for n in archive.namelist()
        if n.startswith('META-INF/')
        and n.count('/') == 1
        and n.upper().endswith(('.RSA', '.DSA', '.EC'))
    )
    digests = []
    for name in names:
        info = archive.getinfo(name)
        if info.file_size > MAX_V1_BLOCK:
            raise FormatError(f'{name} exceeds size limit')
        content = der(archive.read(info)).expect(0x30).children()
        if len(content) != 2 or oid(content[0]) != OID_SIGNED_DATA:
            raise FormatError(f'{name} is not PKCS#7 SignedData')
        fields = content[1].expect(0xA0).children()[0].expect(0x30).children()
        certificates = next((f for f in fields if f.tag == 0xA0), None)
        if certificates is None or not certificates.children():
            raise FormatError(f'{name} has no certificate')
        digest = hashlib.sha256(certificates.children()[0].raw).hexdigest()
        if digest not in digests:
            digests.append(digest)
    return digests


def certificate_digests(apk):
    """SHA-256 hex digests of the APK's signing certificates (not verified)."""
    with open(apk, 'rb') as file:
        pairs = signing_block(file)
    for scheme in (SCHEME_V3, SCHEME_V2):
        if scheme in pairs:
            return scheme_certificates(pairs[scheme])
    with zipfile.ZipFile(apk) as archive:
        digests = v1_certificates(archive)
    if not digests:
        raise FormatError('APK is not signed')
    return digests
