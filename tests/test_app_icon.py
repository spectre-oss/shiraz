# SPDX-License-Identifier: GPL-3.0-only
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from shiraz.apk import FormatError
from shiraz.app_icon import ResourceTable, extract_icon, resolve_label
from shiraz.cli import main

ANDROID = 'http://schemas.android.com/apk/res/android'
PNG = b'\x89PNG\r\n\x1a\n' + bytes(24)
WEBP = b'RIFF' + bytes(4) + b'WEBPVP8 ' + bytes(16)
REF, STRING, COLOR = 0x01, 0x03, 0x1C


def pool(strings, utf8=False):
    """ResStringPool chunk."""
    offsets, data = [], bytearray()
    for text in strings:
        offsets.append(len(data))
        if utf8:
            raw = text.encode('utf-8')
            data += bytes([len(text), len(raw)]) + raw + b'\0'
        else:
            data += struct.pack('<H', len(text)) + text.encode('utf-16-le') + b'\0\0'
    while len(data) % 4:
        data += b'\0'
    header = 28
    start = header + 4 * len(strings)
    body = b''.join(struct.pack('<I', o) for o in offsets) + data
    return struct.pack('<HHIIIIII', 1, header, header + len(body), len(strings), 0, 0x100 if utf8 else 0, start, 0) + body


def binary_xml(elements):
    """elements: [(tag, [(name, data_type, data)], children)] -> AXML bytes (android-namespaced attributes)."""
    strings = [ANDROID]

    def index(text):
        if text not in strings:
            strings.append(text)
        return strings.index(text)

    chunks = bytearray()

    def emit(tag, attrs, children):
        nonlocal chunks
        body = struct.pack('<IIHHHHHH', 0xFFFFFFFF, index(tag), 20, 20, len(attrs), 0, 0, 0)
        for name, data_type, data in attrs:
            # A str value is a plain string attribute without namespace (manifest package).
            if isinstance(data, str):
                body += struct.pack('<IIIHBBI', 0xFFFFFFFF, index(name), index(data), 8, 0, STRING, index(data))
            else:
                body += struct.pack('<IIIHBBI', 0, index(name), 0xFFFFFFFF, 8, 0, data_type, data)
        chunks += struct.pack('<HHIII', 0x102, 16, 16 + len(body), 1, 0xFFFFFFFF) + body
        for child in children:
            emit(*child)
        chunks += struct.pack('<HHIIIII', 0x103, 16, 24, 1, 0xFFFFFFFF, 0xFFFFFFFF, index(tag))

    for element in elements:
        emit(*element)
    pooled = pool(strings)
    return struct.pack('<HHI', 3, 8, 8 + len(pooled) + len(chunks)) + pooled + bytes(chunks)


def manifest(icon, label=None):
    attrs = [('icon', REF, icon)] + ([('label', REF, label)] if label else [])
    return binary_xml([('manifest', [('package', STRING, 'sample.app')], [('application', attrs, [])])])


def type_chunk(type_id, entries, density=0, flags=0, language=b'\0\0\0\0'):
    """entries: {entry_index: (data_type, data, compact)}; flags 1=sparse, 2=offset16."""
    config = struct.pack('<III', 28, 0, 0)[:8] + language + struct.pack('<BBH', 0, 0, density) + bytes(12)
    blobs, offsets = bytearray(), {}
    for index, (data_type, data, compact) in sorted(entries.items()):
        offsets[index] = len(blobs)
        if compact:
            blobs += struct.pack('<HHI', 0, 0x0008 | (data_type << 8), data)
        else:
            blobs += struct.pack('<HHI', 8, 0, 0) + struct.pack('<HBBI', 8, 0, data_type, data)
    count = max(entries) + 1 if not flags & 1 else len(entries)
    if flags & 1:
        table = b''.join(struct.pack('<HH', i, offsets[i] // 4) for i in sorted(offsets))
    elif flags & 2:
        table = b''.join(struct.pack('<H', offsets[i] // 4 if i in offsets else 0xFFFF) for i in range(count))
    else:
        table = b''.join(struct.pack('<I', offsets.get(i, 0xFFFFFFFF)) for i in range(count))
    while len(table) % 4:
        table += b'\xff\xff'
    header = 20 + len(config)
    entries_start = header + len(table)
    return struct.pack('<HHIBBHII', 0x0201, header, entries_start + len(blobs), type_id, flags, 0, count, entries_start) + config + table + bytes(blobs)


def table(strings, chunks, utf8=True):
    package_header = 288
    name = 'sample.app'.encode('utf-16-le').ljust(256, b'\0')
    body = b''.join(chunks)
    package = struct.pack('<HHII', 0x0200, package_header, package_header + len(body), 0x7F) + name + struct.pack('<IIIII', 0, 0, 0, 0, 0)
    package += body
    pooled = pool(strings, utf8)
    return struct.pack('<HHII', 2, 12, 12 + len(pooled) + len(package), 1) + pooled + package


class AppIconTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def apk(self, files, icon=0x7F010000, label=None):
        path = self.root / 'app.apk'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('AndroidManifest.xml', manifest(icon, label))
            for name, data in files.items():
                archive.writestr(name, data)
        return path

    def test_highest_density_raster_wins_over_adaptive(self):
        strings = ['res/a.png', 'res/b.webp', 'res/c.xml']
        arsc = table(strings, [
            type_chunk(1, {0: (STRING, 0, False)}, density=240),
            type_chunk(1, {0: (STRING, 1, True)}, density=640, flags=2),
            type_chunk(1, {0: (STRING, 2, False)}, density=0xFFFE, flags=1),
        ])
        apk = self.apk({'resources.arsc': arsc, 'res/a.png': PNG, 'res/b.webp': WEBP})
        before = apk.read_bytes()
        record = extract_icon(apk, self.root / 'out')
        self.assertEqual(record['kind'], 'bitmap')
        self.assertEqual((self.root / 'out' / record['image']).read_bytes(), WEBP)
        self.assertEqual(json.loads((self.root / 'out/icon.json').read_text())['image'], 'icon.webp')
        self.assertEqual(apk.read_bytes(), before)

    def test_adaptive_icon_layers_through_references_and_bitmap(self):
        # 0x7f010000 -> adaptive XML; foreground -> 0x7f020001 -> ref 0x7f020002 -> png; background color inline.
        adaptive = binary_xml([('adaptive-icon', [], [
            ('background', [('drawable', COLOR, 0xFF336699)], []),
            ('foreground', [('drawable', REF, 0x7F020001)], []),
        ])])
        bitmap = binary_xml([('bitmap', [('src', REF, 0x7F020003)], [])])
        arsc = table(['res/adaptive.xml', 'res/fg.png', 'res/bitmap.xml'], [
            type_chunk(1, {0: (STRING, 0, False)}, density=0xFFFE),
            type_chunk(2, {1: (REF, 0x7F020002, True), 2: (STRING, 2, False), 3: (STRING, 1, False)}),
        ])
        apk = self.apk({'resources.arsc': arsc, 'res/adaptive.xml': adaptive, 'res/fg.png': PNG, 'res/bitmap.xml': bitmap})
        record = extract_icon(apk, self.root / 'out')
        self.assertEqual(record['kind'], 'adaptive')
        self.assertEqual(record['background_color'], '#ff336699')
        self.assertEqual((self.root / 'out' / record['foreground']).read_bytes(), PNG)

    def test_sparse_lookup_and_utf16_pool(self):
        arsc = table(['res/x.png', 'res/y.png'], [type_chunk(1, {3: (STRING, 0, False), 9: (STRING, 1, True)}, density=480, flags=1)], utf8=False)
        resources = ResourceTable(arsc)
        self.assertEqual(resources.values(0x7F010009), [(480, STRING, 1)])
        self.assertEqual(resources.values(0x7F010004), [])
        self.assertEqual(resources.values(0x7F020000), [])
        apk = self.apk({'resources.arsc': arsc, 'res/y.png': PNG}, icon=0x7F010009)
        self.assertEqual(extract_icon(apk, self.root / 'out')['image'], 'icon.png')

    def test_vector_icon_is_reported_unsupported(self):
        adaptive = binary_xml([('adaptive-icon', [], [('foreground', [('drawable', REF, 0x7F020000)], [])])])
        vector = binary_xml([('vector', [], [])])
        arsc = table(['res/a.xml', 'res/v.xml'], [type_chunk(1, {0: (STRING, 0, False)}), type_chunk(2, {0: (STRING, 1, False)})])
        apk = self.apk({'resources.arsc': arsc, 'res/a.xml': adaptive, 'res/v.xml': vector})
        with self.assertRaisesRegex(FormatError, 'vector'):
            extract_icon(apk, self.root / 'out')

    def test_rejects_malformed_and_unsafe_inputs(self):
        good = table(['res/a.png'], [type_chunk(1, {0: (STRING, 0, False)})])
        for broken in (good[:-4], b'\x02\x00\x0c\x00' + struct.pack('<I', 9999) + good[8:], good[:12] + b'\xff' * 8 + good[20:]):
            with self.assertRaises(FormatError):
                ResourceTable(broken)
        unsafe = table(['res/../../etc/passwd.png'], [type_chunk(1, {0: (STRING, 0, False)})])
        with self.assertRaisesRegex(FormatError, 'unsafe'):
            extract_icon(self.apk({'resources.arsc': unsafe}), self.root / 'out')
        not_png = self.apk({'resources.arsc': good, 'res/a.png': b'GIF89a' + bytes(20)})
        with self.assertRaisesRegex(FormatError, 'not a supported image'):
            extract_icon(not_png, self.root / 'out')
        loop = table([], [type_chunk(1, {0: (REF, 0x7F010000, False)})])
        with self.assertRaisesRegex(FormatError, 'too deep'):
            extract_icon(self.apk({'resources.arsc': loop}), self.root / 'out')
        (self.root / 'link').symlink_to(self.root)
        with self.assertRaisesRegex(FormatError, 'real directory'):
            extract_icon(self.apk({'resources.arsc': good, 'res/a.png': PNG}), self.root / 'link')

    def test_corrupt_pool_missing_layer_and_reference_fanout_fail_cleanly(self):
        good = table(['res/a.png'], [type_chunk(1, {0: (STRING, 0, False)})])
        start = 12 + 28 + 4  # Table header, pool header, one offset: the UTF-8 length bytes.
        corrupt = bytearray(good)
        corrupt[start:start + 2] = b'\x7f\x7f'
        with self.assertRaisesRegex(FormatError, 'string pool'):
            ResourceTable(bytes(corrupt))
        with self.assertRaisesRegex(FormatError, 'missing res/a.png'):
            extract_icon(self.apk({'resources.arsc': good}), self.root / 'out')
        # Every level references the next through many configurations.
        levels = [type_chunk(1, {i: (REF, 0x7F010000 + i + 1, False) for i in range(8)} | {8: (STRING, 0, False)}, density=d)
                  for d in range(100, 140)]
        fanout = table(['res/a.png'], levels)
        with self.assertRaisesRegex(FormatError, 'budget'):
            extract_icon(self.apk({'resources.arsc': fanout, 'res/a.png': PNG}), self.root / 'out')

    def test_label_prefers_default_locale_and_follows_references(self):
        arsc = table(['Bonjour', 'Hello', 'res/a.png'], [
            type_chunk(3, {0: (STRING, 0, False)}, language=b'fr\0\0'),
            type_chunk(3, {0: (STRING, 1, False), 1: (REF, 0x7F030000, True)}),
        ])
        self.assertEqual(resolve_label(self.apk({'resources.arsc': arsc}, label=0x7F030001)), 'Hello')
        loop = table([], [type_chunk(3, {0: (REF, 0x7F030000, False)})])
        with self.assertRaisesRegex(FormatError, 'no string value'):
            resolve_label(self.apk({'resources.arsc': loop}, label=0x7F030000))
        with self.assertRaisesRegex(FormatError, 'no application label'):
            resolve_label(self.apk({'resources.arsc': arsc}))

    def test_cli_reports_missing_icon(self):
        apk = self.apk({'resources.arsc': table([], [])}, icon=0)
        self.assertEqual(main(['icon', str(apk), '--output-dir', str(self.root / 'out')]), 1)
        self.assertFalse((self.root / 'out').exists())


if __name__ == '__main__':
    unittest.main()
