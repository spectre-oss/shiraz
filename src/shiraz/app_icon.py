# SPDX-License-Identifier: GPL-3.0-only
"""Bounded resources.arsc lookup and launcher-icon extraction.

Chunk layouts follow AOSP libandroidfw ResourceTypes.h: UTF-8/UTF-16 string
pools, dense and sparse (FLAG_SPARSE) type chunks, 16-bit offsets
(FLAG_OFFSET16) and compact entries (FLAG_COMPACT). Only the lookups needed to
resolve an icon are implemented; the APK is never modified.
"""
import json
import struct
import zipfile
from pathlib import Path, PurePosixPath

from .apk import FormatError
from .manifest import ANDROID, BinaryXml, ManifestError, inspect_manifest

MAX_TABLE = 64 * 1024 * 1024
MAX_XML = 1024 * 1024
MAX_IMAGE = 8 * 1024 * 1024
MAX_DEPTH = 8
MAX_MANIFEST = 4 * 1024 * 1024
# Total resource values visited per icon, so crafted reference fan-out stays cheap.
MAX_VISITS = 4096
NO_ENTRY = 0xFFFFFFFF
DENSITY_ANY = 0xFFFE
DENSITY_NONE = 0xFFFF
TYPE_REFERENCE, TYPE_STRING = 0x01, 0x03
TYPE_COLORS = range(0x1C, 0x20)
IMAGE_MAGIC = {'.png': (b'\x89PNG\r\n\x1a\n',), '.webp': (b'RIFF',), '.jpg': (b'\xff\xd8\xff',), '.jpeg': (b'\xff\xd8\xff',)}


class ResourceTable:
    """Index of a resources.arsc: global string pool plus type chunks per (package, type)."""

    def __init__(self, data):
        if len(data) > MAX_TABLE:
            raise FormatError('resources.arsc exceeds size limit')
        self.data = data
        self.xml = BinaryXml(data)
        self.xml.utf8_errors = 'replace'
        self.chunks = {}
        kind, header, total = self._unpack('<HHI', 0)
        if kind != 0x0002 or header < 12 or total != len(data):
            raise FormatError('invalid resources.arsc header')
        pos, strings = header, None
        while pos < total:
            kind, size, length = self._chunk(pos, total)
            if kind == 0x0001 and strings is None:
                try:
                    self.xml.pool(pos, size, pos + length)
                except (ManifestError, UnicodeDecodeError) as exc:
                    raise FormatError(f'resources.arsc string pool: {exc}') from None
                strings = self.xml.strings
            elif kind == 0x0200:
                self._package(pos, size, pos + length)
            pos += length
        self.strings = strings or []

    def _unpack(self, fmt, pos, end=None):
        try:
            return self.xml.unpack(fmt, pos, end)
        except ManifestError as exc:
            raise FormatError(f'resources.arsc: {exc}') from None

    def _chunk(self, pos, end):
        kind, size, length = self._unpack('<HHI', pos, end)
        if size < 8 or length < size or pos + length > end or length % 4:
            raise FormatError('resources.arsc chunk outside parent')
        return kind, size, length

    def _package(self, start, header, end):
        if header < 284:
            raise FormatError('short resources.arsc package header')
        package, = self._unpack('<I', start + 8, end)
        if not 0 < package < 256:
            return
        pos = start + header
        while pos < end:
            kind, size, length = self._chunk(pos, end)
            if kind == 0x0201:
                if size < 20 + 4:
                    raise FormatError('short resources.arsc type header')
                type_id, = self._unpack('<B', pos + 8, end)
                chunks = self.chunks.setdefault((package, type_id), [])
                if len(chunks) >= 4096:
                    raise FormatError('too many resources.arsc type configurations')
                chunks.append((pos, size, pos + length))
            pos += length

    def values(self, res_id, locale=False):
        """Every configuration's (density, data_type, data) for one resource ID.

        With `locale`, each tuple also carries the config's packed language
        and region (0 for the default, unqualified locale)."""
        key, entry = ((res_id >> 24) & 0xFF, (res_id >> 16) & 0xFF), res_id & 0xFFFF
        result = []
        for start, header, end in self.chunks.get(key, ()):
            _, flags, _, count, entries_start = self._unpack('<BBHII', start + 8, end)
            config_size, = self._unpack('<I', start + 20, end)
            density = language = 0
            if config_size >= 16 and header >= 20 + 16:
                language, = self._unpack('<I', start + 20 + 8, end)
                density, = self._unpack('<H', start + 20 + 14, end)
            offset = self._entry_offset(start + header, flags, count, entry, end)
            if offset is None:
                continue
            value = self._entry_value(start + entries_start + offset, end)
            if value is not None:
                result.append((density,) + value + ((language,) if locale else ()))
        return result

    def _entry_offset(self, table, flags, count, entry, end):
        if count > 65536:
            raise FormatError('resources.arsc entry count exceeds limit')
        if flags & 0x01:
            low, high = 0, count
            while low < high:  # Sparse entries are sorted by index.
                middle = (low + high) // 2
                index, offset16 = self._unpack('<HH', table + middle * 4, end)
                if index == entry:
                    return offset16 * 4
                low, high = (middle + 1, high) if index < entry else (low, middle)
            return None
        if entry >= count:
            return None
        if flags & 0x02:
            offset16, = self._unpack('<H', table + entry * 2, end)
            return None if offset16 == 0xFFFF else offset16 * 4
        offset, = self._unpack('<I', table + entry * 4, end)
        return None if offset == NO_ENTRY else offset

    def _entry_value(self, pos, end):
        size, flags = self._unpack('<HH', pos, end)
        if flags & 0x0008:  # Compact: type in the high flag byte, data inline.
            data, = self._unpack('<I', pos + 4, end)
            return flags >> 8, data
        if flags & 0x0001:  # Complex bags (styles, arrays) are not icons.
            return None
        _, _, data_type, data = self._unpack('<HBBI', pos + size, end)
        return data_type, data

    def string(self, index):
        if index >= len(self.strings):
            raise FormatError('resources.arsc string index outside pool')
        return self.strings[index]


class IconResolver:
    def __init__(self, archive, table):
        self.archive = archive
        self.table = table
        self.visits = 0
        # Typed attribute values are keyed by id(element); keeping every parsed
        # root alive stops a collected element's id from being reused.
        self.documents = []
        self.xml_values = {}

    def read(self, name, limit):
        parts = PurePosixPath(name).parts
        if not name.startswith('res/') or '..' in parts or '\\' in name:
            raise FormatError(f'unsafe resource path {name!r}')
        try:
            info = self.archive.getinfo(name)
        except KeyError:
            raise FormatError(f'APK is missing {name}') from None
        if info.file_size > limit:
            raise FormatError(f'{name} exceeds size limit')
        return self.archive.read(info)

    def candidates(self, res_id, depth=0):
        """Flatten references into (density, kind, value): kind is 'file' or 'color'."""
        if depth > MAX_DEPTH:
            raise FormatError('resource reference chain too deep')
        found = []
        for density, data_type, data in self.table.values(res_id):
            self.visits += 1
            if self.visits > MAX_VISITS:
                raise FormatError('launcher icon references exceed lookup budget')
            if data_type == TYPE_REFERENCE and data:
                found += self.candidates(data, depth + 1)
            elif data_type == TYPE_STRING:
                found.append((density, 'file', self.table.string(data)))
            elif data_type in TYPE_COLORS:
                found.append((density, 'color', data))
        return found

    @staticmethod
    def rank(density):
        return 160 if density in (0, DENSITY_NONE) else density

    def raster(self, res_id):
        files = [(self.rank(d), v) for d, kind, v in self.candidates(res_id)
                 if kind == 'file' and d != DENSITY_ANY and image_suffix(v)]
        return max(files)[1] if files else None

    def layer(self, element, depth):
        """One adaptive-icon layer: a color, or a raster file reached through bitmap/inset/layer XML."""
        if depth > MAX_DEPTH:
            raise FormatError('drawable nesting too deep')
        attrs = (('{%s}drawable' % ANDROID), ('{%s}src' % ANDROID))
        for attr in attrs:
            typed = self.xml_values.get((id(element), attr))
            if not typed:
                continue
            data_type, data, _ = typed
            if data_type in TYPE_COLORS:
                return {'color': data}
            if data_type == TYPE_REFERENCE and data:
                return self.drawable(data, depth + 1)
        for child in element:
            if child.tag in ('bitmap', 'inset', 'layer-list', 'item'):
                found = self.layer(child, depth + 1)
                if found:
                    return found
        return None

    def drawable(self, res_id, depth):
        raster = self.raster(res_id)
        if raster:
            return {'file': raster}
        for _, kind, value in self.candidates(res_id):
            if kind == 'color':
                return {'color': value}
        for _, kind, value in self.candidates(res_id):
            if kind == 'file' and value.endswith('.xml'):
                root = self.parse_xml(value)
                if root.tag in ('bitmap', 'inset', 'layer-list'):
                    return self.layer(root, depth)
                raise FormatError(f'unsupported icon drawable <{root.tag}> (vector icons are not rendered yet)')
        return None

    def parse_xml(self, name):
        document = BinaryXml(self.read(name, MAX_XML))
        try:
            root = document.parse()
        except (ManifestError, UnicodeDecodeError) as exc:
            raise FormatError(f'{name}: {exc}') from None
        self.documents.append(root)
        self.xml_values.update(document.typed_values)
        return root

    def resolve(self, res_id):
        raster = self.raster(res_id)
        if raster:
            return {'kind': 'bitmap', 'image': raster}
        for _, kind, value in self.candidates(res_id):
            if kind != 'file' or not value.endswith('.xml'):
                continue
            root = self.parse_xml(value)
            if root.tag != 'adaptive-icon':
                raise FormatError(f'unsupported launcher icon <{root.tag}>')
            layers = {}
            for child in root:
                if child.tag in ('background', 'foreground'):
                    layers[child.tag] = self.layer(child, 0)
            if not layers.get('foreground') and not layers.get('background'):
                raise FormatError('adaptive icon has no supported layers')
            return {'kind': 'adaptive', **layers}
        raise FormatError('launcher icon has no supported image')


def resolve_label(apk):
    """The application label in the default locale, following references."""
    with zipfile.ZipFile(apk) as archive:
        try:
            manifest_info = archive.getinfo('AndroidManifest.xml')
            if manifest_info.file_size > MAX_MANIFEST:
                raise FormatError('AndroidManifest.xml exceeds size limit')
            manifest = inspect_manifest(archive.read(manifest_info))
        except KeyError:
            raise FormatError('APK is missing AndroidManifest.xml') from None
        except (ManifestError, UnicodeDecodeError) as exc:
            raise FormatError(f'manifest: {exc}') from None
        res_id = manifest.get('application_label_resource', 0)
        literal = manifest.get('application_label')
        if not res_id:
            if literal and not literal.startswith('@'):
                return literal
            raise FormatError('APK declares no application label')
        try:
            table_info = archive.getinfo('resources.arsc')
        except KeyError:
            raise FormatError('APK is missing resources.arsc') from None
        if table_info.file_size > MAX_TABLE:
            raise FormatError('resources.arsc exceeds size limit')
        table = ResourceTable(archive.read(table_info))
    for _ in range(MAX_DEPTH):
        values = table.values(res_id, locale=True)
        values.sort(key=lambda value: value[3] != 0)  # Default locale first.
        if not values:
            break
        _, data_type, data, _ = values[0]
        if data_type == TYPE_STRING:
            label = table.string(data).strip()
            if label:
                return label[:200]
            break
        if data_type != TYPE_REFERENCE or not data:
            break
        res_id = data
    raise FormatError('application label has no string value')


def image_suffix(name):
    suffix = PurePosixPath(name).suffix.lower()
    if suffix not in IMAGE_MAGIC or name.lower().endswith('.9.png'):
        return None
    return suffix


def extract_icon(apk, output):
    """Write the APK launcher icon into `output` as icon.json plus unchanged image files."""
    output = Path(output)
    if output.is_symlink() or (output.exists() and not output.is_dir()):
        raise FormatError('icon output must be a real directory')
    with zipfile.ZipFile(apk) as archive:
        try:
            manifest_info = archive.getinfo('AndroidManifest.xml')
            table_info = archive.getinfo('resources.arsc')
            if manifest_info.file_size > MAX_MANIFEST:
                raise FormatError('AndroidManifest.xml exceeds size limit')
            manifest = inspect_manifest(archive.read(manifest_info))
        except KeyError as exc:
            raise FormatError(f'APK is missing {exc.args[0]}') from None
        except (ManifestError, UnicodeDecodeError) as exc:
            raise FormatError(f'manifest: {exc}') from None
        res_id = manifest.get('application_icon', 0)
        if not res_id:
            raise FormatError('APK declares no application icon')
        if table_info.file_size > MAX_TABLE:
            raise FormatError('resources.arsc exceeds size limit')
        resolver = IconResolver(archive, ResourceTable(archive.read(table_info)))
        icon = resolver.resolve(res_id)
        output.mkdir(parents=True, exist_ok=True)
        record = {'schema': 1, 'kind': icon['kind'], 'resource': f'0x{res_id:08x}'}

        def store(name, source):
            suffix = image_suffix(source)
            data = resolver.read(source, MAX_IMAGE)
            if not suffix or not data.startswith(IMAGE_MAGIC[suffix]):
                raise FormatError(f'{source} is not a supported image')
            target = output / (name + ('.jpg' if suffix == '.jpeg' else suffix))
            if target.is_symlink():
                raise FormatError('icon output file must not be a symlink')
            target.write_bytes(data)
            return target.name

        if icon['kind'] == 'bitmap':
            record['image'] = store('icon', icon['image'])
        else:
            for name in ('background', 'foreground'):
                layer = icon.get(name)
                if layer and 'file' in layer:
                    record[name] = store(name, layer['file'])
                elif layer and 'color' in layer:
                    record[name + '_color'] = f"#{layer['color']:08x}"
        (output / 'icon.json').write_text(json.dumps(record, indent=2, sort_keys=True) + '\n')
        return record
