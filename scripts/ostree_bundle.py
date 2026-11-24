#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pure-Python GVariant, OSTree static-delta, and Flatpak single-file bundle builder.

Implements the canonical GLib GVariant binary format and libostree static-delta
superblock (`OSTREE_STATIC_DELTA_SUPERBLOCK_FORMAT` with `inline-parts`) used by
`flatpak build-bundle`, plus the prebuilt `SHIRAZFP` base archive consumed by
the Go/WASM browser builder.
"""

from __future__ import annotations

import base64
import hashlib
import json
import lzma
import os
from pathlib import Path
import re
import stat
import struct
import time

FLATPAK_MAGIC_U32 = 0xE5890001
DEFAULT_RUNTIME_BRANCH = '24.08'
DEFAULT_RUNTIME_REPO = 'https://flathub.org/repo/flathub.flatpakrepo'
FLATPAK_BASE_MAGIC = b'SHIRAZFP'
FLATPAK_BASE_VERSION = 1
APP_ID_PREFIX = 'io.github.spectre_oss.shiraz'

OSTREE_OBJECT_FILE = 1
OSTREE_OBJECT_DIR_TREE = 2
OSTREE_OBJECT_DIR_META = 3
OSTREE_OBJECT_COMMIT = 4

OSTREE_OP_OPEN_SPLICE_AND_CLOSE = ord('S')
OSTREE_DELTAPART_VERSION = 0


# ---------------------------------------------------------------------------
# Flatpak App ID & metadata helpers (mirrored in go-wasm/flatpak.go)
# ---------------------------------------------------------------------------


def flatpak_app_id(package: str) -> str:
    """Derive a valid Flatpak reverse-DNS App ID from an Android package name."""
    cleaned = re.sub(r'[^A-Za-z0-9_]+', '_', package).strip('_')
    if not cleaned:
        cleaned = 'app'
    if cleaned[0].isdigit():
        cleaned = 'p' + cleaned
    candidate = f'{APP_ID_PREFIX}.{cleaned}'
    if len(candidate) > 255:
        digest = hashlib.sha256(package.encode('utf-8')).hexdigest()[:12]
        max_body = 255 - len(APP_ID_PREFIX) - 1 - 1 - len(digest)
        candidate = f'{APP_ID_PREFIX}.{cleaned[:max_body]}_{digest}'
    return candidate


def flatpak_metadata_ini(
    app_id: str,
    architecture: str,
    runtime_branch: str = DEFAULT_RUNTIME_BRANCH,
    allow_network: bool = True,
) -> str:
    """Return the canonical Flatpak /metadata INI text."""
    shared = 'ipc;network;' if allow_network else 'ipc;'
    return (
        '[Application]\n'
        f'name={app_id}\n'
        f'runtime=org.freedesktop.Platform/{architecture}/{runtime_branch}\n'
        'command=shiraz-flatpak-run\n'
        '\n'
        '[Context]\n'
        f'shared={shared}\n'
        'sockets=wayland;fallback-x11;pulseaudio;\n'
        'devices=dri;\n'
        'filesystems=xdg-run/pipewire-0:ro;\n'
        '\n'
        '[Session Bus Policy]\n'
        'org.freedesktop.Notifications=talk\n'
        'org.freedesktop.portal.Desktop=talk\n'
    )


def desktop_escape(value: str) -> str:
    return (
        value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    )


def flatpak_desktop_entry(app_id: str, title: str) -> str:
    return (
        '[Desktop Entry]\n'
        'Type=Application\n'
        'Version=1.5\n'
        f'Name={desktop_escape(title)} (Shiraz)\n'
        'Exec=shiraz-flatpak-run\n'
        f'Icon={app_id}\n'
        'Categories=Utility;\n'
        'Terminal=false\n'
        'X-Purism-FormFactor=Workstation;Mobile;\n'
    )


def xml_escape(value: str) -> str:
    return (
        value.replace('&', '&amp;')
        .replace('<', '&lt;')
        .replace('>', '&gt;')
        .replace('"', '&quot;')
        .replace("'", '&apos;')
    )


def flatpak_metainfo_xml(app_id: str, title: str, package: str, version: str) -> str:
    safe_id = xml_escape(app_id)
    safe_title = xml_escape(title)
    safe_pkg = xml_escape(package)
    safe_ver = xml_escape(version)
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<component type="desktop-application">\n'
        f'  <id>{safe_id}</id>\n'
        '  <metadata_license>CC0-1.0</metadata_license>\n'
        '  <project_license>GPL-3.0-only</project_license>\n'
        f'  <name>{safe_title} (Shiraz)</name>\n'
        f'  <summary>Android app ({safe_pkg}) packaged with Shiraz</summary>\n'
        f'  <launchable type="desktop-id">{safe_id}.desktop</launchable>\n'
        '  <releases>\n'
        f'    <release version="{safe_ver}" />\n'
        '  </releases>\n'
        '</component>\n'
    )


# ---------------------------------------------------------------------------
# Canonical GLib GVariant binary encoder
# ---------------------------------------------------------------------------


def _align_up(offset: int, alignment: int) -> int:
    if alignment <= 1:
        return offset
    rem = offset % alignment
    return offset if rem == 0 else offset + (alignment - rem)


def _pad_to(buf: bytearray, alignment: int) -> None:
    target = _align_up(len(buf), alignment)
    if target > len(buf):
        buf.extend(b'\x00' * (target - len(buf)))


def _offset_width(body_len: int, n_offsets: int) -> int:
    for width, limit in ((1, 0xFF), (2, 0xFFFF), (4, 0xFFFFFFFF), (8, 0xFFFFFFFFFFFFFFFF)):
        if body_len + n_offsets * width <= limit:
            return width
    raise OverflowError('GVariant container exceeds 64-bit limit')


def _append_offset(buf: bytearray, value: int, width: int) -> None:
    buf.extend(value.to_bytes(width, 'little'))


class GVValue:
    __slots__ = ('sig', 'align', 'fixed', 'data')

    def __init__(self, sig: str, align: int, fixed: bool, data: bytes):
        self.sig = sig
        self.align = align
        self.fixed = fixed
        self.data = data


def gv_byte(val: int) -> GVValue:
    return GVValue('y', 1, True, bytes((val & 0xFF,)))


def gv_bool(val: bool) -> GVValue:
    return GVValue('b', 1, True, b'\x01' if val else b'\x00')


def gv_u32_le(val: int) -> GVValue:
    return GVValue('u', 4, True, struct.pack('<I', val & 0xFFFFFFFF))


def gv_u32_be(val: int) -> GVValue:
    """uint32 stored in big-endian byte order (OSTree GUINT32_TO_BE convention)."""
    return GVValue('u', 4, True, struct.pack('>I', val & 0xFFFFFFFF))


def gv_u64_le(val: int) -> GVValue:
    return GVValue('t', 8, True, struct.pack('<Q', val & 0xFFFFFFFFFFFFFFFF))


def gv_u64_be(val: int) -> GVValue:
    """uint64 stored in big-endian byte order (OSTree GUINT64_TO_BE convention)."""
    return GVValue('t', 8, True, struct.pack('>Q', val & 0xFFFFFFFFFFFFFFFF))


def gv_string(text: str) -> GVValue:
    return GVValue('s', 1, False, text.encode('utf-8') + b'\x00')


def gv_bytes(raw: bytes) -> GVValue:
    return GVValue('ay', 1, False, bytes(raw))


def gv_variant(child: GVValue) -> GVValue:
    return GVValue('v', 8, False, child.data + b'\x00' + child.sig.encode('ascii'))


def gv_tuple(items: list[GVValue], sig: str | None = None) -> GVValue:
    if not items:
        return GVValue('()', 1, True, b'\x00')
    align = max(item.align for item in items)
    fixed = all(item.fixed for item in items)
    computed_sig = sig or ('(' + ''.join(item.sig for item in items) + ')')
    buf = bytearray()
    offsets: list[int] = []
    last_idx = len(items) - 1
    for i, item in enumerate(items):
        _pad_to(buf, item.align)
        buf.extend(item.data)
        if not item.fixed and i < last_idx:
            offsets.append(len(buf))
    if fixed:
        _pad_to(buf, align)
    elif offsets:
        width = _offset_width(len(buf), len(offsets))
        # GVariant tuple framing offsets are written in reverse order.
        for offset in reversed(offsets):
            _append_offset(buf, offset, width)
    return GVValue(computed_sig, align, fixed, bytes(buf))


def gv_dict_entry(key: GVValue, value: GVValue) -> GVValue:
    return gv_tuple([key, value], sig='{' + key.sig + value.sig + '}')


def gv_array(elem_sig: str, elem_align: int, elem_fixed: bool, items: list[GVValue]) -> GVValue:
    sig = 'a' + elem_sig
    if not items:
        return GVValue(sig, elem_align, False, b'')
    buf = bytearray()
    if elem_fixed:
        for item in items:
            buf.extend(item.data)
        return GVValue(sig, elem_align, False, bytes(buf))
    offsets: list[int] = []
    for item in items:
        _pad_to(buf, elem_align)
        buf.extend(item.data)
        offsets.append(len(buf))
    width = _offset_width(len(buf), len(offsets))
    for offset in offsets:
        _append_offset(buf, offset, width)
    return GVValue(sig, elem_align, False, bytes(buf))


# ---------------------------------------------------------------------------
# OSTree object formats & checksums
# ---------------------------------------------------------------------------


def ostree_varuint64(n: int) -> bytes:
    if n < 0:
        raise ValueError('varuint64 requires non-negative integer')
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        if n:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            break
    return bytes(out)


def ostree_checksum_b64(digest: bytes) -> str:
    """OSTree modified base64 of a 32-byte SHA-256 digest (43 chars, '/' -> '_')."""
    if len(digest) != 32:
        raise ValueError('expected 32-byte digest')
    return base64.b64encode(digest).decode('ascii').rstrip('=').replace('/', '_')


def ostree_delta_part_key(to_digest: bytes, part_index: int) -> str:
    b64 = ostree_checksum_b64(to_digest)
    return f'deltas/{b64[:2]}/{b64[2:]}/{part_index}'


def ostree_dirmeta_bytes(mode: int = 0o040755, uid: int = 0, gid: int = 0) -> bytes:
    empty_xattrs = gv_array('(ayay)', 1, False, [])
    return gv_tuple([gv_u32_be(uid), gv_u32_be(gid), gv_u32_be(mode), empty_xattrs]).data


def ostree_file_checksum(
    mode: int, content: bytes = b'', symlink_target: str = '', uid: int = 0, gid: int = 0
) -> bytes:
    empty_xattrs = gv_array('(ayay)', 1, False, [])
    header = gv_tuple(
        [
            gv_u32_be(uid),
            gv_u32_be(gid),
            gv_u32_be(mode),
            gv_u32_be(0),
            gv_string(symlink_target),
            empty_xattrs,
        ]
    ).data
    h = hashlib.sha256()
    h.update(struct.pack('>I', len(header)))
    h.update(b'\x00\x00\x00\x00')
    h.update(header)
    if stat.S_ISREG(mode):
        h.update(content)
    return h.digest()


def ostree_dirtree_bytes(
    files: list[tuple[str, bytes]], dirs: list[tuple[str, bytes, bytes]]
) -> bytes:
    sorted_files = sorted(files, key=lambda item: item[0].encode('utf-8'))
    sorted_dirs = sorted(dirs, key=lambda item: item[0].encode('utf-8'))
    gv_files = gv_array(
        '(say)',
        1,
        False,
        [gv_tuple([gv_string(name), gv_bytes(csum)]) for name, csum in sorted_files],
    )
    gv_dirs = gv_array(
        '(sayay)',
        1,
        False,
        [
            gv_tuple([gv_string(name), gv_bytes(tree_csum), gv_bytes(meta_csum)])
            for name, tree_csum, meta_csum in sorted_dirs
        ],
    )
    return gv_tuple([gv_files, gv_dirs]).data


def ostree_commit_value(
    root_tree: bytes,
    root_meta: bytes,
    ref: str,
    metadata_ini: str,
    installed_size: int,
    download_size: int,
    subject: str,
    timestamp: int,
) -> GVValue:
    entries = [
        gv_dict_entry(gv_string('ostree.collection-binding'), gv_variant(gv_string(''))),
        gv_dict_entry(
            gv_string('ostree.ref-binding'),
            gv_variant(gv_array('s', 1, False, [gv_string(ref)])),
        ),
        gv_dict_entry(gv_string('xa.ref'), gv_variant(gv_string(ref))),
        gv_dict_entry(gv_string('xa.metadata'), gv_variant(gv_string(metadata_ini))),
        gv_dict_entry(gv_string('xa.installed-size'), gv_variant(gv_u64_be(installed_size))),
        gv_dict_entry(gv_string('xa.download-size'), gv_variant(gv_u64_be(download_size))),
    ]
    meta_dict = gv_array('{sv}', 8, False, entries)
    related = gv_array('(say)', 1, False, [])
    return gv_tuple(
        [
            meta_dict,
            gv_bytes(b''),
            related,
            gv_string(subject),
            gv_string(''),
            gv_u64_be(timestamp),
            gv_bytes(root_tree),
            gv_bytes(root_meta),
        ]
    )


# ---------------------------------------------------------------------------
# Static delta part & superblock builder
# ---------------------------------------------------------------------------


class DeltaPartBuilder:
    """Builds a single OSTree static delta part `(y@ay)` + meta entry."""

    def __init__(self) -> None:
        self.modes: list[tuple[int, int, int]] = []
        self.mode_index: dict[tuple[int, int, int], int] = {}
        self.payload = bytearray()
        self.ops = bytearray()
        self.objects = bytearray()
        self.seen: set[tuple[int, bytes]] = set()
        self.uncompressed_size = 0

    def _intern_mode(self, uid: int, gid: int, mode: int) -> int:
        key = (uid, gid, mode)
        idx = self.mode_index.get(key)
        if idx is None:
            idx = len(self.modes)
            self.mode_index[key] = idx
            self.modes.append(key)
        return idx

    def add_meta(self, objtype: int, data: bytes) -> bytes:
        digest = hashlib.sha256(data).digest()
        key = (objtype, digest)
        if key in self.seen:
            return digest
        self.seen.add(key)
        offset = len(self.payload)
        self.payload.extend(data)
        self.ops.append(OSTREE_OP_OPEN_SPLICE_AND_CLOSE)
        self.ops.extend(ostree_varuint64(len(data)))
        self.ops.extend(ostree_varuint64(offset))
        self.objects.append(objtype)
        self.objects.extend(digest)
        self.uncompressed_size += len(data)
        return digest

    def add_file(self, mode: int, content: bytes, uid: int = 0, gid: int = 0) -> bytes:
        digest = ostree_file_checksum(mode, content=content, uid=uid, gid=gid)
        key = (OSTREE_OBJECT_FILE, digest)
        if key in self.seen:
            return digest
        self.seen.add(key)
        mode_idx = self._intern_mode(uid, gid, mode)
        offset = len(self.payload)
        self.payload.extend(content)
        self.ops.append(OSTREE_OP_OPEN_SPLICE_AND_CLOSE)
        self.ops.extend(ostree_varuint64(mode_idx))
        self.ops.extend(ostree_varuint64(0))  # empty xattrs index 0
        self.ops.extend(ostree_varuint64(len(content)))
        self.ops.extend(ostree_varuint64(offset))
        self.objects.append(OSTREE_OBJECT_FILE)
        self.objects.extend(digest)
        self.uncompressed_size += len(content)
        return digest

    def add_symlink(self, target: str, uid: int = 0, gid: int = 0) -> bytes:
        mode = stat.S_IFLNK | 0o777
        digest = ostree_file_checksum(mode, symlink_target=target, uid=uid, gid=gid)
        key = (OSTREE_OBJECT_FILE, digest)
        if key in self.seen:
            return digest
        self.seen.add(key)
        mode_idx = self._intern_mode(uid, gid, mode)
        raw = target.encode('utf-8')
        offset = len(self.payload)
        self.payload.extend(raw)
        self.ops.append(OSTREE_OP_OPEN_SPLICE_AND_CLOSE)
        self.ops.extend(ostree_varuint64(mode_idx))
        self.ops.extend(ostree_varuint64(0))
        self.ops.extend(ostree_varuint64(len(raw)))
        self.ops.extend(ostree_varuint64(offset))
        self.objects.append(OSTREE_OBJECT_FILE)
        self.objects.extend(digest)
        self.uncompressed_size += len(raw)
        return digest

    def finish(self, compress: bool = True, preset: int = 6) -> dict:
        gv_modes = gv_array(
            '(uuu)',
            4,
            True,
            [
                gv_tuple([gv_u32_be(uid), gv_u32_be(gid), gv_u32_be(mode)])
                for uid, gid, mode in self.modes
            ],
        )
        # Single empty xattrs entry at index 0.
        gv_xattrs = gv_array('a(ayay)', 1, False, [gv_array('(ayay)', 1, False, [])])
        part_payload = gv_tuple(
            [gv_modes, gv_xattrs, gv_bytes(bytes(self.payload)), gv_bytes(bytes(self.ops))]
        ).data
        if compress:
            body = lzma.compress(
                part_payload, format=lzma.FORMAT_XZ, check=lzma.CHECK_CRC64, preset=preset
            )
            comp_byte = ord('x')
        else:
            body = part_payload
            comp_byte = 0
        # Serialized GVariant "(yay)" is byte 0 (compression type) + body bytes.
        part_bytes = bytes((comp_byte,)) + body
        part_sha256 = hashlib.sha256(part_bytes).digest()
        return {
            'part_bytes': part_bytes,
            'sha256': part_sha256,
            'compressed_size': len(part_bytes),
            'uncompressed_size': self.uncompressed_size,
            'objects': bytes(self.objects),
        }


def build_superblock(
    ref: str,
    metadata_ini: str,
    commit_val: GVValue,
    parts: list[dict],
    timestamp: int,
    runtime_repo: str = DEFAULT_RUNTIME_REPO,
) -> bytes:
    """Serialize a canonical Flatpak single-file `.flatpak` static-delta superblock."""
    to_digest = hashlib.sha256(commit_val.data).digest()
    dict_entries = [
        # Flatpak MIME header must come first in metadata a{sv}.
        gv_dict_entry(gv_string('flatpak'), gv_variant(gv_u32_le(FLATPAK_MAGIC_U32))),
        gv_dict_entry(gv_string('ref'), gv_variant(gv_string(ref))),
        gv_dict_entry(gv_string('metadata'), gv_variant(gv_string(metadata_ini))),
    ]
    if runtime_repo:
        dict_entries.append(
            gv_dict_entry(gv_string('runtime-repo'), gv_variant(gv_string(runtime_repo)))
        )
    dict_entries.append(gv_dict_entry(gv_string('collection-id'), gv_variant(gv_string(''))))
    dict_entries.append(
        gv_dict_entry(gv_string('ostree.endianness'), gv_variant(gv_byte(ord('l'))))
    )

    part_metas = []
    for index, part in enumerate(parts):
        part_key = ostree_delta_part_key(to_digest, index)
        part_gv = GVValue('(yay)', 1, False, part['part_bytes'])
        dict_entries.append(gv_dict_entry(gv_string(part_key), gv_variant(part_gv)))
        part_metas.append(
            gv_tuple(
                [
                    gv_u32_le(OSTREE_DELTAPART_VERSION),
                    gv_bytes(part['sha256']),
                    gv_u64_le(part['compressed_size']),
                    gv_u64_le(part['uncompressed_size']),
                    gv_bytes(part['objects']),
                ]
            )
        )

    superblock = gv_tuple(
        [
            gv_array('{sv}', 8, False, dict_entries),
            gv_u64_be(timestamp),
            gv_bytes(b''),
            gv_bytes(to_digest),
            commit_val,
            gv_bytes(b''),
            gv_array('(uayttay)', 8, False, part_metas),
            gv_array('(yaytt)', 8, False, []),
        ]
    )
    return superblock.data


def _ingest_directory(
    directory: Path,
    part: DeltaPartBuilder,
    dirmeta_digest: bytes,
    skip_rel: set[str] | None = None,
    base_dir: Path | None = None,
) -> tuple[bytes, bytes, int]:
    """Recursively ingest a filesystem directory into `part`.

    Returns `(dirtree_digest, dirmeta_digest, installed_bytes)`.
    """
    if base_dir is None:
        base_dir = directory
    files: list[tuple[str, bytes]] = []
    dirs: list[tuple[str, bytes, bytes]] = []
    installed = 0
    for entry in sorted(directory.iterdir(), key=lambda p: p.name.encode('utf-8')):
        rel = entry.relative_to(base_dir).as_posix()
        if skip_rel and rel in skip_rel:
            continue
        st = entry.lstat()
        if stat.S_ISLNK(st.st_mode):
            target = os.readlink(entry)
            csum = part.add_symlink(target)
            files.append((entry.name, csum))
            installed += len(target.encode('utf-8'))
        elif stat.S_ISDIR(st.st_mode):
            sub_tree, sub_meta, sub_bytes = _ingest_directory(
                entry, part, dirmeta_digest, skip_rel=skip_rel, base_dir=base_dir
            )
            dirs.append((entry.name, sub_tree, sub_meta))
            installed += sub_bytes
        elif stat.S_ISREG(st.st_mode):
            raw = entry.read_bytes()
            mode = stat.S_IFREG | (0o755 if (st.st_mode & 0o111) else 0o644)
            csum = part.add_file(mode, raw)
            files.append((entry.name, csum))
            installed += len(raw)
        else:
            raise RuntimeError(f'Unsupported file type in Flatpak tree: {entry}')
    tree_bytes = ostree_dirtree_bytes(files, dirs)
    tree_digest = part.add_meta(OSTREE_OBJECT_DIR_TREE, tree_bytes)
    return tree_digest, dirmeta_digest, installed


def build_flatpak_bundle_from_tree(
    stage_dir: Path,
    app_id: str,
    architecture: str,
    branch: str = 'stable',
    runtime_repo: str = DEFAULT_RUNTIME_REPO,
    compress: bool = True,
    timestamp: int | None = None,
) -> bytes:
    """Build a standalone `.flatpak` bundle from a Flatpak commit directory (`metadata`, `files/`, `export/`)."""
    metadata_path = stage_dir / 'metadata'
    if not metadata_path.is_file():
        raise RuntimeError('Flatpak staging directory missing metadata file')
    metadata_ini = metadata_path.read_text(encoding='utf-8')
    part = DeltaPartBuilder()
    dirmeta = part.add_meta(OSTREE_OBJECT_DIR_META, ostree_dirmeta_bytes(0o040755))
    root_tree, root_meta, installed = _ingest_directory(stage_dir, part, dirmeta)
    part_info = part.finish(compress=compress)
    ts = int(time.time()) if timestamp is None else int(timestamp)
    ref = f'app/{app_id}/{architecture}/{branch}'
    commit_val = ostree_commit_value(
        root_tree=root_tree,
        root_meta=root_meta,
        ref=ref,
        metadata_ini=metadata_ini,
        installed_size=installed,
        download_size=part_info['compressed_size'],
        subject=f'Export {app_id}',
        timestamp=ts,
    )
    return build_superblock(
        ref=ref,
        metadata_ini=metadata_ini,
        commit_val=commit_val,
        parts=[part_info],
        timestamp=ts,
        runtime_repo=runtime_repo,
    )


def build_flatpak_base_from_files_dir(
    files_dir: Path,
    version: str,
    architecture: str,
    runtime_branch: str = DEFAULT_RUNTIME_BRANCH,
    runtime_repo: str = DEFAULT_RUNTIME_REPO,
    compress: bool = True,
) -> bytes:
    """Build a reusable `SHIRAZFP` browser Flatpak base from a no-AOT `/files` directory.

    The returned bytes hold:
      - 8-byte magic `"SHIRAZFP"`
      - uint32_le version (`1`)
      - uint32_le JSON index byte length
      - UTF-8 JSON index describing prebuilt runtime subtree digests and Part 0 metadata
      - Raw Part 0 `(yay)` bytes (verbatim LZMA-compressed OSTree static delta part)
    """
    required = (
        files_dir / 'bin/shiraz-flatpak-run',
        files_dir / 'usr/bin/shiraz-app',
        files_dir / 'usr/lib',
        files_dir / 'usr/art',
        files_dir / 'usr/share/shiraz',
    )
    for req in required:
        if not req.exists():
            raise RuntimeError(f'Flatpak base staging missing {req}')

    skip_shiraz = {
        'original.apk',
        'launch.properties',
        'application.properties',
        'bundle.json',
        'files.sha256.json',
    }
    part = DeltaPartBuilder()
    dirmeta = part.add_meta(OSTREE_OBJECT_DIR_META, ostree_dirmeta_bytes(0o040755))

    bin_tree, bin_meta, bin_size = _ingest_directory(files_dir / 'bin', part, dirmeta)
    usr_bin_tree, usr_bin_meta, usr_bin_size = _ingest_directory(
        files_dir / 'usr/bin', part, dirmeta
    )
    usr_lib_tree, usr_lib_meta, usr_lib_size = _ingest_directory(
        files_dir / 'usr/lib', part, dirmeta
    )
    usr_art_tree, usr_art_meta, usr_art_size = _ingest_directory(
        files_dir / 'usr/art', part, dirmeta
    )

    shiraz_dir = files_dir / 'usr/share/shiraz'
    shiraz_static_files: list[list[str]] = []
    shiraz_static_dirs: list[list[str]] = []
    shiraz_size = 0
    for entry in sorted(shiraz_dir.iterdir(), key=lambda p: p.name.encode('utf-8')):
        if entry.name in skip_shiraz or entry.name == 'installer':
            continue
        st = entry.lstat()
        if stat.S_ISLNK(st.st_mode):
            target = os.readlink(entry)
            csum = part.add_symlink(target)
            shiraz_static_files.append([entry.name, csum.hex()])
            shiraz_size += len(target.encode('utf-8'))
        elif stat.S_ISDIR(st.st_mode):
            sub_tree, sub_meta, sub_bytes = _ingest_directory(entry, part, dirmeta)
            shiraz_static_dirs.append([entry.name, sub_tree.hex(), sub_meta.hex()])
            shiraz_size += sub_bytes
        elif stat.S_ISREG(st.st_mode):
            raw = entry.read_bytes()
            mode = stat.S_IFREG | (0o755 if (st.st_mode & 0o111) else 0o644)
            csum = part.add_file(mode, raw)
            shiraz_static_files.append([entry.name, csum.hex()])
            shiraz_size += len(raw)

    usr_share_other_dirs: list[list[str]] = []
    other_share_size = 0
    for entry in sorted((files_dir / 'usr/share').iterdir(), key=lambda p: p.name.encode('utf-8')):
        if entry.name == 'shiraz':
            continue
        if entry.is_dir() and not entry.is_symlink():
            sub_tree, sub_meta, sub_bytes = _ingest_directory(entry, part, dirmeta)
            usr_share_other_dirs.append([entry.name, sub_tree.hex(), sub_meta.hex()])
            other_share_size += sub_bytes

    part_info = part.finish(compress=compress)
    installed_size = (
        bin_size + usr_bin_size + usr_lib_size + usr_art_size + shiraz_size + other_share_size
    )

    index = {
        'version': version,
        'architecture': architecture,
        'runtime_branch': runtime_branch,
        'runtime_repo': runtime_repo,
        'dirmeta_0755_sha256': dirmeta.hex(),
        'installed_size': installed_size,
        'part0': {
            'compressed_size': part_info['compressed_size'],
            'uncompressed_size': part_info['uncompressed_size'],
            'sha256': part_info['sha256'].hex(),
            'objects_b64': base64.b64encode(part_info['objects']).decode('ascii'),
        },
        'files_root': {
            'bin': {'tree': bin_tree.hex(), 'meta': bin_meta.hex()},
            'usr_bin': {'tree': usr_bin_tree.hex(), 'meta': usr_bin_meta.hex()},
            'usr_lib': {'tree': usr_lib_tree.hex(), 'meta': usr_lib_meta.hex()},
            'usr_art': {'tree': usr_art_tree.hex(), 'meta': usr_art_meta.hex()},
            'usr_share_shiraz_static_files': shiraz_static_files,
            'usr_share_shiraz_static_dirs': shiraz_static_dirs,
            'usr_share_other_dirs': usr_share_other_dirs,
        },
    }
    index_bytes = json.dumps(index, separators=(',', ':'), sort_keys=True).encode('utf-8')
    header = FLATPAK_BASE_MAGIC + struct.pack('<II', FLATPAK_BASE_VERSION, len(index_bytes))
    return header + index_bytes + part_info['part_bytes']
