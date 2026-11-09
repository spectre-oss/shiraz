# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic independent native-function extents; no effect/execution claims."""

import dataclasses
import struct
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_cached_self_record import SYMBOL
from guest_native_function import read_native_function, read_native_function_at
from test_guest_cached_self_record import fixture

COMMAND = 32 + 152 + 72 + 72 + 24
DATA = 0x4100


def uleb(value):
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def image_fixture(base=0, deltas=(0x1000, 16), tail=b'\0'):
    image, _ = fixture(base)
    payload = b''.join(uleb(delta) for delta in deltas) + tail
    struct.pack_into('<II', image, 16, 5, COMMAND - 32 + 16)
    struct.pack_into('<IIII', image, COMMAND, 0x26, 16, DATA, len(payload))
    image[DATA:DATA + len(payload)] = payload
    return image


class NativeFunctionTests(unittest.TestCase):
    def read(self, image):
        return read_native_function(image, SYMBOL)

    def test_independent_complete_immutable_bytes(self):
        for base in (0, 0x100000000):
            image = image_fixture(base)
            row = self.read(image)
            self.assertEqual((row.entry, row.end), (base + 0x1000, base + 0x1010))
            self.assertEqual(row.body, bytes(image[0x1000:0x1010]))
            image[0x1000] ^= 1
            self.assertNotEqual(row.body, bytes(image[0x1000:0x1010]))
            with self.assertRaises(dataclasses.FrozenInstanceError):
                row.end = 0
            self.assertEqual(set(dataclasses.asdict(row)), {'entry', 'end', 'body'})

    def test_missing_duplicate_and_malformed_command(self):
        image, _ = fixture()
        with self.assertRaises(ValueError):self.read(image)
        for offset, value in ((COMMAND + 4, 8), (COMMAND + 12, 0), (COMMAND + 12, 10_000_002)):
            image = image_fixture();struct.pack_into('<I', image, offset, value)
            with self.subTest(offset=offset, value=value), self.assertRaises(ValueError):self.read(image)
        image = image_fixture()
        image[COMMAND + 16:COMMAND + 32] = image[COMMAND:COMMAND + 16]
        struct.pack_into('<II', image, 16, 6, COMMAND - 32 + 32)
        with self.assertRaisesRegex(ValueError, 'duplicate'):self.read(image)

    def test_linkedit_ownership_and_metadata_overlap(self):
        for location in (0, 0x1000, 0x4000, 0x4010, 0x5000):
            image = image_fixture();struct.pack_into('<I', image, COMMAND + 8, location)
            with self.subTest(location=location), self.assertRaises(ValueError):self.read(image)
        image = image_fixture();struct.pack_into('<I', image, 32 + 152 + 72 + 60, 5)
        with self.assertRaises(ValueError):self.read(image)
        image = image_fixture();struct.pack_into('<I', image, COMMAND + 12, 0x1001)
        with self.assertRaises(ValueError):self.read(image)

    def test_uleb_termination_canonicality_and_overflow(self):
        payloads = (b'\x80', b'\x80\0', b'\x80' * 10 + b'\0', b'\xff' * 9 + b'\x02\0',
                    uleb(0x1000) + uleb(16), uleb(0x1000) + b'\0\x01')
        for payload in payloads:
            image = image_fixture();struct.pack_into('<I', image, COMMAND + 12, len(payload))
            image[DATA:DATA + len(payload)] = payload
            with self.subTest(payload=payload), self.assertRaises(ValueError):self.read(image)
        self.assertEqual(len(self.read(image_fixture(tail=b'\0\0\0')).body), 16)

    def test_every_start_requires_aligned_rx_text_and_symbol_match(self):
        for deltas in ((0x1004, 16), (0x1000,), (0x1000, 2), (0xffc, 4, 16),
                       (0x1000, 0x1000), (0x1000, 16, 2), (0x1000, 16, 0x10000)):
            with self.subTest(deltas=deltas), self.assertRaises(ValueError):self.read(image_fixture(deltas=deltas))
        image = image_fixture();struct.pack_into('<Q', image, 0x4008, 0x1004)
        with self.assertRaises(ValueError):self.read(image)
        image = image_fixture();struct.pack_into('<I', image, 32 + 60, 3)
        with self.assertRaises(ValueError):self.read(image)

    def test_selected_body_size_is_bounded_independently(self):
        for size in (65536, 65540):
            original = image_fixture(deltas=(0x1000, size))
            image = bytearray(0x21000)
            image[:0x4000] = original[:0x4000]
            image[0x20000:0x21000] = original[0x4000:0x5000]
            struct.pack_into('<QQ', image, 32 + 32, 0x20000, 0)
            struct.pack_into('<Q', image, 32 + 48, 0x20000)
            struct.pack_into('<Q', image, 32 + 72 + 40, 0x1f000)
            struct.pack_into('<Q', image, 32 + 152 + 24, 0x40000)
            struct.pack_into('<QQ', image, 32 + 152 + 72 + 24, 0x80000, 0x1000)
            struct.pack_into('<Q', image, 32 + 152 + 72 + 40, 0x20000)
            symtab = 32 + 152 + 72 + 72
            struct.pack_into('<I', image, symtab + 8, 0x20000)
            struct.pack_into('<I', image, symtab + 16, 0x20010)
            struct.pack_into('<I', image, COMMAND + 8, 0x20100)
            if size == 65536:
                self.assertEqual(len(self.read(image).body), size)
            else:
                with self.assertRaisesRegex(ValueError, 'body size bound'):self.read(image)

    def test_function_start_count_bound(self):
        with patch('guest_native_function.MAX_STARTS', 1), \
                self.assertRaisesRegex(ValueError, 'count/address bound'):
            self.read(image_fixture())

    def test_uint64_address_overflow(self):
        image = image_fixture()
        payload = uleb(0x1000) + uleb((1 << 64) - 1) + b'\0'
        image[DATA:DATA + len(payload)] = payload
        struct.pack_into('<I', image, COMMAND + 12, len(payload))
        with self.assertRaisesRegex(ValueError, 'address bound'):self.read(image)

    def test_input_and_symbol_validation(self):
        for image in (b'', image_fixture()[:32], image_fixture()[:0x4000], None):
            with self.subTest(image_type=type(image)), self.assertRaises(ValueError):self.read(image)
        for symbol in ('', None, 'absent', '\0bad'):
            with self.subTest(symbol=symbol), self.assertRaises(ValueError):read_native_function(image_fixture(), symbol)

    def test_target_identity_keeps_aliases_and_independent_bounds(self):
        image = image_fixture()
        names = b'\0' + SYMBOL.encode() + b'\0_alias\0'
        symtab = COMMAND - 24
        struct.pack_into('<IIII', image, symtab + 8, 0x4000, 2, 0x4040, len(names))
        image[0x4040:0x4040+len(names)] = names
        struct.pack_into('<IBBHQ', image, 0x4010, len(SYMBOL.encode()) + 2, 0xf, 1, 0, 0x1000)
        row = read_native_function_at(image, SYMBOL, 0x1000)
        self.assertEqual(row.names, tuple(sorted((SYMBOL, '_alias'))))
        self.assertEqual((row.function.entry, row.function.end), (0x1000, 0x1010))
        self.assertIsNone(read_native_function_at(image, SYMBOL, 0x1004))
        self.assertIsNone(read_native_function_at(image, SYMBOL, 0x3000))
        with patch('guest_native_function.MAX_FUNCTION_ALIASES', 1), \
                self.assertRaisesRegex(ValueError, 'alias budget'):
            read_native_function_at(image, SYMBOL, 0x1000)
        with self.assertRaises(dataclasses.FrozenInstanceError):row.names = ()
        struct.pack_into('<I', image, 0x4010, 1)
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            read_native_function_at(image, SYMBOL, 0x1000)

    def test_selected_target_metadata_cannot_claim_an_invalid_definition(self):
        for field, fmt, value in ((4, '<B', 1), (5, '<B', 0), (6, '<H', 0x100)):
            image = image_fixture()
            # Preserve anchor identity; a second matching symbol must itself
            # be a valid text definition before it can name the target.
            names = b'\0' + SYMBOL.encode() + b'\0_alias\0'
            struct.pack_into('<IIII', image, COMMAND - 24 + 8, 0x4000, 2, 0x4040, len(names))
            image[0x4040:0x4040+len(names)] = names
            struct.pack_into('<IBBHQ', image, 0x4010, len(SYMBOL.encode()) + 2, 0xf, 1, 0, 0x1000)
            struct.pack_into(fmt, image, 0x4010+field, value)
            with self.subTest(field=field), self.assertRaises(ValueError):
                read_native_function_at(image, SYMBOL, 0x1000)
        for target in (True, -4, 2, 1 << 64):
            with self.subTest(target=target), self.assertRaises(ValueError):
                read_native_function_at(image_fixture(), SYMBOL, target)

    def test_partial_section_overlap_cannot_bound_selected_bytes(self):
        image = image_fixture()
        total = 32 + struct.unpack_from('<I', image, 20)[0]
        insertion = 32 + 152
        image[insertion+80:total+80] = bytes(image[insertion:total])
        image[insertion:insertion+80] = struct.pack(
            '<16s16sQQIIIIIIII', b'__alias'.ljust(16, b'\0'), b'__TEXT'.ljust(16, b'\0'),
            0x1008, 4, 0x1008, 2, 0, 0, 0, 0, 0, 0)
        struct.pack_into('<I', image, 32 + 4, 232)
        struct.pack_into('<I', image, 32 + 64, 2)
        struct.pack_into('<I', image, 20, total + 80 - 32)
        with self.assertRaisesRegex(ValueError, 'ownership'):
            self.read(image)


if __name__ == '__main__':
    unittest.main()
