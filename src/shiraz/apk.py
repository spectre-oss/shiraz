# SPDX-License-Identifier: GPL-3.0-only
"""Bounded APK/DEX inventory; deliberately not a reachability/tree-shaking proof."""

import hashlib
import marshal
import os
import re
import struct
import zipfile
from pathlib import Path, PurePosixPath
from .architecture import supports_native_abis
from .elf import ElfError, inspect_elf
from .manifest import ManifestError, inspect_manifest

FRAMEWORK = ('Landroid/', 'Lcom/android/', 'Ldalvik/', 'Llibcore/')
MAX_ENTRY = 128 * 1024 * 1024
# Large browser engines fit while total archive and per-library work stay bounded.
MAX_NATIVE_ENTRY = 256 * 1024 * 1024
# Game data packs are inventoried without reading/decompressing their contents.
MAX_ASSET_ENTRY = 384 * 1024 * 1024
MAX_TOTAL = 768 * 1024 * 1024


class FormatError(ValueError):
    pass


class Dex:
    def __init__(self, data):
        self.data = data
        if len(data) < 112 or data[:8] not in [
            b'dex\n' + v + b'\0' for v in (b'035', b'037', b'038', b'039', b'040')
        ]:
            raise FormatError('unsupported or truncated DEX (supported: 035, 037–040)')
        if self.u32(32) != len(data) or self.u32(36) != 112 or self.u32(40) != 0x12345678:
            raise FormatError('invalid DEX header size, file size, or endian tag')
        self.strings = []
        for off in self.table(56, 4):
            pos = self.u32(off)
            # Skip bounded ULEB128 UTF-16 length; descriptors/method names are ASCII.
            for _ in range(5):
                self.bounds(pos, 1)
                byte = data[pos]
                pos += 1
                if byte < 128:
                    break
            else:
                raise FormatError('invalid string ULEB128')
            end = data.find(b'\0', pos)
            if end < 0 or end - pos > 1024 * 1024:
                raise FormatError('unterminated or oversized DEX string')
            self.strings.append(data[pos:end].decode('utf-8', errors='replace'))
        self.types = [self.index(self.strings, self.u32(off)) for off in self.table(64, 4)]
        self.protos = []
        for off in self.table(72, 12):
            result = self.index(self.types, self.u32(off + 4))
            params_off = self.u32(off + 8)
            params = ''
            if params_off:
                count = self.u32(params_off)
                self.bounds(params_off + 4, count * 2)
                params = ''.join(
                    self.index(self.types, self.u16(params_off + 4 + i * 2)) for i in range(count)
                )
            self.protos.append('(' + params + ')' + result)
        self.methods = []
        for off in self.table(88, 8):
            owner = self.index(self.types, self.u16(off))
            name = self.index(self.strings, self.u32(off + 4))
            proto = self.index(self.protos, self.u16(off + 2))
            self.methods.append(owner + '->' + name + proto)
        self.defined = {self.index(self.types, self.u32(off)) for off in self.table(96, 32)}
        self.fields = []
        for off in self.table(80, 8):
            owner = self.index(self.types, self.u16(off))
            kind = self.index(self.types, self.u16(off + 2))
            name = self.index(self.strings, self.u32(off + 4))
            self.fields.append(owner + '->' + name + ':' + kind)

    def bounds(self, offset, size):
        if offset < 0 or size < 0 or offset + size > len(self.data):
            raise FormatError('DEX offset outside file')

    def u32(self, off):
        self.bounds(off, 4)
        return struct.unpack_from('<I', self.data, off)[0]

    def u16(self, off):
        self.bounds(off, 2)
        return struct.unpack_from('<H', self.data, off)[0]

    def table(self, header, width):
        count, offset = self.u32(header), self.u32(header + 4)
        if count and offset < 112:
            raise FormatError('DEX table overlaps header')
        self.bounds(offset, count * width)
        return range(offset, offset + count * width, width)

    @staticmethod
    def index(values, i):
        if i >= len(values):
            raise FormatError('DEX index outside table')
        return values[i]


def framework(descriptor):
    return descriptor.lstrip('[').startswith(FRAMEWORK)


_MEM_INSPECT_CACHE: dict[tuple[str, bool, int, int], dict] = {}


def _fast_inspect_cache_path(path: Path, require_dex: bool) -> Path | None:
    if os.environ.get('SHIRAZ_FAST_INSPECT') != '1':
        return None
    base = os.environ.get('XDG_CACHE_HOME', '')
    cache_root = Path(base) if base and Path(base).is_absolute() else Path.home() / '.cache'
    cache_dir = cache_root / 'shiraz' / 'inspect'
    key = f'{path.resolve()}:{int(require_dex)}'.encode('utf-8')
    return cache_dir / (hashlib.sha256(key).hexdigest() + '.marshal')


def inspect_apk(path, *, require_dex=True):
    path = Path(path)
    cache_path = _fast_inspect_cache_path(path, require_dex)
    st = None
    mem_key = None
    if cache_path is not None:
        try:
            st = path.stat()
            mem_key = (str(path.resolve()), bool(require_dex), st.st_size, st.st_mtime_ns)
            hit = _MEM_INSPECT_CACHE.get(mem_key)
            if hit is not None:
                return hit
            if cache_path.is_file():
                cached = marshal.loads(cache_path.read_bytes())
                if (
                    isinstance(cached, dict)
                    and cached.get('_size') == st.st_size
                    and cached.get('_mtime_ns') == st.st_mtime_ns
                    and isinstance(cached.get('report'), dict)
                ):
                    _MEM_INSPECT_CACHE[mem_key] = cached['report']
                    return cached['report']
        except (OSError, ValueError, EOFError, TypeError):
            pass
    sha = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            sha.update(chunk)
    try:
        archive = zipfile.ZipFile(path)
    except zipfile.BadZipFile as exc:
        raise FormatError('not an APK/ZIP archive') from exc
    dex_files, native, all_types, methods, fields, defined = [], [], set(), set(), set(), set()
    with archive:
        entries = archive.infolist()
        if len(entries) > 100_000 or sum(i.file_size for i in entries) > MAX_TOTAL:
            raise FormatError('archive exceeds processing limits')
        names = set()
        for entry in entries:
            name = entry.filename
            parts = PurePosixPath(name).parts
            if (
                name in names
                or name.startswith('/')
                or '..' in parts
                or '\\' in name
                or '\x00' in name
            ):
                raise FormatError('duplicate or unsafe archive entry')
            names.add(name)
            limit = MAX_NATIVE_ENTRY if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name) else MAX_ENTRY
            if name.startswith('assets/'):
                limit = MAX_ASSET_ENTRY
            if entry.flag_bits & 1 or entry.file_size > limit:
                raise FormatError('encrypted or oversized archive entry')
        if 'AndroidManifest.xml' not in names:
            raise FormatError('APK has no AndroidManifest.xml')
        try:
            manifest = inspect_manifest(archive.read('AndroidManifest.xml'))
        except ManifestError as exc:
            raise FormatError(str(exc)) from exc
        for entry in entries:
            name = entry.filename
            if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', name):
                dex = Dex(archive.read(entry))
                dex_files.append(
                    {
                        'name': name,
                        'types': len(dex.types),
                        'methods': len(dex.methods),
                        'classes': len(dex.defined),
                    }
                )
                all_types.update(dex.types)
                methods.update(dex.methods)
                fields.update(dex.fields)
                defined.update(dex.defined)
            if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name):
                abi = name.split('/')[1]
                with archive.open(entry) as stream:
                    header = stream.read(20)
                if len(header) < 20 or header[:4] != b'\x7fELF' or header[5] != 1:
                    raise FormatError('native library has invalid or unsupported ELF header')
                bits, machine = header[4], struct.unpack_from('<H', header, 18)[0]
                expected = {
                    'x86_64': (2, 62),
                    'x86': (1, 3),
                    'arm64-v8a': (2, 183),
                    'armeabi-v7a': (1, 40),
                }
                if abi in expected and (bits, machine) != expected[abi]:
                    raise FormatError('native library ABI directory disagrees with ELF header')
                try:
                    elf = inspect_elf(archive.read(entry))
                except ElfError as exc:
                    raise FormatError(str(exc)) from exc
                native.append(
                    {'name': name, 'abi': abi, 'machine': machine, 'bytes': entry.file_size, **elf}
                )
    if require_dex and not dex_files:
        raise FormatError('APK has no supported classes.dex files')
    abis = sorted({item['abi'] for item in native})
    report = {
        'schema': 1,
        'apk_sha256': sha.hexdigest(),
        'manifest': manifest,
        'dex': sorted(dex_files, key=lambda x: x['name']),
        'framework_types': sorted(t for t in all_types - defined if framework(t)),
        'framework_methods': sorted(
            m for m in methods if framework(m.split('->')[0]) and m.split('->')[0] not in defined
        ),
        'framework_fields': sorted(
            f for f in fields if framework(f.split('->')[0]) and f.split('->')[0] not in defined
        ),
        'native_libraries': sorted(native, key=lambda x: x['name']),
        'native_abis': abis,
        'host_native_candidate': supports_native_abis(abis),
        'analysis_kind': 'conservative DEX identifier inventory; not call-graph reachability',
        'limitations': [
            'Reflection, JNI, dynamic loading and resource XML may introduce additional API requirements.',
            'Native ELF inventory does not resolve imported symbols or Bionic ABI compatibility.',
            'Only an experimental programmatic Activity/View/Canvas startup subset is available. Resources, services, input dispatch, redraw and bundling remain incomplete.',
        ],
        'runnable': None,
        'compatibility_verified': False,
    }
    if cache_path is not None and st is not None:
        try:
            cache_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            tmp = cache_path.with_name(f'.{cache_path.name}.tmp.{os.getpid()}')
            tmp.write_bytes(
                marshal.dumps({'_size': st.st_size, '_mtime_ns': st.st_mtime_ns, 'report': report})
            )
            os.replace(tmp, cache_path)
            if mem_key is not None:
                _MEM_INSPECT_CACHE[mem_key] = report
        except (OSError, ValueError, TypeError):
            pass
    return report
