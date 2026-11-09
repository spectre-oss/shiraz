# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic Mach-O/record negative controls, not runtime or TLS proof."""

import copy
import struct
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_cached_self_record import validate_cached_self_binding

TABLE_OFFSETS = (0x10000, 0x20000, 0x30000, 0x40000)
WORDS = (
    0xD2800008, 0x910003E9, 0xD53BD06A, 0, 0, 0, 0, 0, 0, 0, 0,
    0x14000004, 0x91002108, 0xF140211F, 0x540002E0, 0x8B08016F,
    0xF8BFC1E0, 0xF100081F, 0xFA402944, 0x54FFFF20, 0xF868698F,
    0xEB0A01FF, 0x54FFFEC1, 0xF86869AF, 0xF86869D0, 0xEB0F013F,
    0xFA502122, 0x54FFFE22, 0x8B08016F, 0xF8BFC1EF, 0xEB0001FF,
    0x54FFFDA1, 0x8B08018F, 0xF8BFC1EF, 0xEB0A01FF, 0x54FFFD21,
    0xD65F03C0, 0xD4200020,
)


def name(value):
    return value.encode().ljust(16, b'\0')


def fixture(base=0):
    entry = base + 0x1000
    words = list(WORDS)
    for index, register, offset in zip((3, 5, 7, 9), (11, 12, 13, 14), TABLE_OFFSETS):
        address = base + offset
        pages = ((address & ~4095) - ((entry + index * 4) & ~4095)) // 4096
        encoded = pages & ((1 << 21) - 1)
        words[index] = 0x90000000 | (encoded & 3) << 29 | (encoded >> 2) << 5 | register
        words[index + 1] = 0x91000000 | (address & 4095) << 10 | register << 5 | register
    body = struct.pack('<38I', *words)
    text = struct.pack('<II16sQQQQiiII', 0x19, 152, name('__TEXT'), base, 0x4000, 0, 0x4000, 7, 5, 1, 0)
    text += struct.pack('<16s16sQQIIIIIIII', name('__text'), name('__TEXT'), entry, 152, 0x1000, 2, 0, 0, 0x80000400, 0, 0, 0)
    data = struct.pack('<II16sQQQQiiII', 0x19, 72, name('__DATA'), base + 0x10000, 0x38000, 0, 0, 3, 3, 0, 0)
    link = struct.pack('<II16sQQQQiiII', 0x19, 72, name('__LINKEDIT'), base + 0x50000, 0x1000, 0x4000, 0x1000, 1, 1, 0, 0)
    strings = b'\0_shiraz_guest_cached_pthread_self\0'
    symbols = struct.pack('<IIIIII', 2, 24, 0x4000, 1, 0x4010, len(strings))
    commands = text + data + link + symbols
    image = bytearray(0x5000)
    image[:32] = struct.pack('<IiiIIIII', 0xFEEDFACF, 0x100000C, 0, 6 if base == 0 else 2, 4, len(commands), 0, 0)
    image[32:32 + len(commands)] = commands
    image[0x1000:0x1000 + len(body)] = body
    image[0x4000:0x4010] = struct.pack('<IBBHQ', 1, 0xF, 1, 0, entry)
    image[0x4010:0x4010 + len(strings)] = strings
    binding = {
        'abi': 'prepared/pthread-self-noargs', 'symbol': 'pthread_self', 'version': 'LIBC',
        'provider': 'libc.so', 'kind': 'function', 'target': None, 'provider_elf_sha256': None,
        'nonreturning': False, 'addend': 0, 'relocation_type': 1026,
        'cached_self': {
            'profile': 1, 'verification': 'mapped-first-party-code-and-cache-ranges', 'execution_claim': False,
            'entry_offset': 0x1000, 'body_hex': body.hex(), 'stack_exclusion_profile': 1,
            'tables': [{'role': role, 'offset': offset, 'bytes': 32768}
                       for role, offset in zip(('owners', 'keys', 'low', 'high'), TABLE_OFFSETS)],
        },
    }
    return image, binding


class CachedSelfRecordTests(unittest.TestCase):
    def test_independent_zero_fill_data_and_executable_header_normalization(self):
        for base in (0, 1 << 32):
            image, binding = fixture(base)
            result = validate_cached_self_binding(binding, image)
            self.assertEqual(result.effects.entry, base + 0x1000)
            self.assertEqual(result.effects.body_size, 152)
            self.assertEqual([table.address for table in result.effects.tables],
                             [base + offset for offset in TABLE_OFFSETS])
            self.assertEqual(result.record.entry_offset, 0x1000)
            with self.assertRaises(FrozenInstanceError):
                result.record.entry_offset = 4

    def test_no_abi_or_snapshot_or_trusted_bytes_grants_effects(self):
        image, binding = fixture()
        self.assertIsNone(validate_cached_self_binding({'abi': 'legacy'}))
        self.assertIsNone(validate_cached_self_binding(binding))
        del binding['cached_self']
        self.assertIsNone(validate_cached_self_binding(binding, image))

    def test_every_binding_claim_is_exact(self):
        _, binding = fixture()
        for field in ('abi', 'symbol', 'version', 'provider', 'kind', 'target',
                      'provider_elf_sha256', 'nonreturning', 'addend', 'relocation_type'):
            changed = copy.deepcopy(binding);del changed[field]
            with self.subTest(missing=field), self.assertRaises(ValueError):
                validate_cached_self_binding(changed)
        for field, value in [('abi', 'native/pthread-self-noargs'), ('symbol', 'other'),
                             ('provider', 'other.so'), ('version', 'OTHER'), ('kind', 'object'),
                             ('target', 1), ('provider_elf_sha256', '0' * 64), ('nonreturning', True),
                             ('addend', False), ('addend', 1), ('relocation_type', 257)]:
            changed = copy.deepcopy(binding);changed[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_cached_self_binding(changed)

    def test_schema_and_ranges_fail_before_any_effect_grant(self):
        _, binding = fixture()
        cases = [('profile', True), ('profile', 2), ('execution_claim', True),
                 ('verification', 'trusted'), ('entry_offset', False), ('entry_offset', (1 << 64) - 4),
                 ('entry_offset', 3), ('body_hex', '00'), ('body_hex', 'FF' * 152),
                 ('stack_exclusion_profile', True), ('stack_exclusion_profile', 2), ('extra', 1)]
        for field, value in cases:
            changed = copy.deepcopy(binding);changed['cached_self'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_cached_self_binding(changed)
        for field, value in [('role', 'keys'), ('offset', True), ('offset', 3),
                             ('offset', 0x1000), ('offset', (1 << 64) - 8), ('bytes', True), ('bytes', 8)]:
            changed = copy.deepcopy(binding);changed['cached_self']['tables'][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_cached_self_binding(changed)
        changed = copy.deepcopy(binding);changed['cached_self']['tables'].reverse()
        with self.assertRaises(ValueError):validate_cached_self_binding(changed)

    def test_record_bytes_or_instruction_tables_cannot_override_file(self):
        image, binding = fixture()
        changed = copy.deepcopy(binding);changed['cached_self']['body_hex'] = '00' * 152
        with self.assertRaisesRegex(ValueError, 'mapped/file'):validate_cached_self_binding(changed, image)
        changed = copy.deepcopy(binding);changed['cached_self']['entry_offset'] += 4
        with self.assertRaisesRegex(ValueError, 'independent symbol'):validate_cached_self_binding(changed, image)
        changed = copy.deepcopy(binding);changed['cached_self']['tables'][0]['offset'] += 8
        with self.assertRaisesRegex(ValueError, 'instruction/record'):validate_cached_self_binding(changed, image)
        changed = bytearray(image);struct.pack_into('<I', changed, 0x1000, 0xD503201F)
        binding['cached_self']['body_hex'] = bytes(changed[0x1000:0x1098]).hex()
        with self.assertRaisesRegex(ValueError, 'unsupported'):validate_cached_self_binding(binding, changed)

    def test_header_command_segment_and_protection_corruption_rejected(self):
        image, binding = fixture()
        # Absolute file offsets of header or segment declaration fields.
        mutations = [(0, 0), (4, 0x1000007), (12, 1), (16, 4097), (20, len(image)),
                     (32 + 4, 151), (32 + 60, 7), (32 + 152 + 60, 5),
                     (32 + 152 + 32, 1), (32 + 152 + 72 + 40, 0)]
        for offset, value in mutations:
            changed = bytearray(image);struct.pack_into('<I', changed, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                validate_cached_self_binding(binding, changed)

    def test_symbol_must_be_unique_defined_text_and_not_resolver(self):
        image, binding = fixture()
        for offset, fmt, value in [(0x4000 + 4, '<B', 1), (0x4000 + 4, '<B', 0x2F),
                                   (0x4000 + 5, '<B', 0), (0x4000 + 6, '<H', 0x100),
                                   (0x4000 + 8, '<Q', 0x1004), (0x4000, '<I', 99999)]:
            changed = bytearray(image);struct.pack_into(fmt, changed, offset, value)
            with self.subTest(offset=offset, value=value), self.assertRaises(ValueError):
                validate_cached_self_binding(binding, changed)
        changed = bytearray(image)
        symtab = 32 + 152 + 72 + 72
        strings = b'\0_shiraz_guest_cached_pthread_self\0'
        struct.pack_into('<IIII', changed, symtab + 8, 0x4000, 2, 0x4020, len(strings))
        changed[0x4010:0x4020] = changed[0x4000:0x4010]
        changed[0x4020:0x4020 + len(strings)] = strings
        with self.assertRaisesRegex(ValueError, 'duplicate'):validate_cached_self_binding(binding, changed)

    def test_truncated_metadata_and_code_are_rejected(self):
        image, binding = fixture()
        for length in (0, 31, 32, 128, 0x1097, 0x4001, len(image) - 1):
            with self.subTest(length=length), self.assertRaises(ValueError):
                validate_cached_self_binding(binding, image[:length])

    def test_debug_observation_is_neither_definition_nor_duplicate(self):
        image, binding = fixture()
        changed = bytearray(image)
        symtab = 32 + 152 + 72 + 72
        strings = b'\0_shiraz_guest_cached_pthread_self\0'
        struct.pack_into('<IIII', changed, symtab + 8, 0x4000, 2, 0x4020, len(strings))
        changed[0x4010:0x4020] = changed[0x4000:0x4010]
        changed[0x4014] = 0x24  # N_FUN/STABS, not N_SECT.
        struct.pack_into('<Q', changed, 0x4018, 0xDEADBEEF)
        changed[0x4020:0x4020 + len(strings)] = strings
        self.assertIsNotNone(validate_cached_self_binding(binding, changed))
        changed[0x4004] = 0x24
        with self.assertRaisesRegex(ValueError, 'missing'):
            validate_cached_self_binding(binding, changed)
