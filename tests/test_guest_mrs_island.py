# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic island byte controls; never native, TLS or APK acceptance."""

import hashlib
import struct
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_mrs_island import validate_mrs_island

UINT64_END = 1 << 64


def branch(origin, destination):
    # Deliberately unbounded encoder so range-negative tests can supply modulo
    # forgeries without the fixture preempting the verifier's rejection.
    return 0x14000000 | (((destination - origin) // 4) & 0x03FFFFFF)


def fixture(*, island=0x2000, site=0x1000, target=0, entry=0x5000, accessor=0x6000):
    blob = struct.pack('<8IQQIIQ', 0xA9BF47F0, 0x10000130, 0x580000D1, 0xD61F0220,
                       0xA8C147F0, 0xA93F7FFF, branch(island + 24, site + 4), 0xD503201F,
                       entry, island + 16, target, 1, accessor)
    arguments = {"island": island, "site": site, "target": target,
                 "replacement": branch(site, island), "entry": entry, "accessor": accessor}
    return blob, arguments


class MrsIslandTests(unittest.TestCase):
    def test_complete_default_snapshot_has_explicit_immutable_scope(self):
        blob, args = fixture();result = validate_mrs_island(blob, **args)
        self.assertIsNotNone(result)
        self.assertEqual(struct.unpack_from('<I', blob, 24)[0], 0x17FFFBFB)
        self.assertEqual(args['replacement'], 0x14000400)
        self.assertEqual((result.continuation, result.context, result.return_branch_pc),
                         (0x1004, 0x2028, 0x2018))
        self.assertEqual(result.body_sha256, hashlib.sha256(blob).hexdigest())
        self.assertEqual((result.body_size, result.default_kind), (64, 1))
        self.assertEqual((result.outer_stack_write_bytes, result.outer_stack_clear_bytes,
                          result.outer_stack_offset), (16, 16, -16))
        self.assertTrue(result.requires_verified_entry_and_accessor and
                        result.requires_verified_code_mappings and
                        result.requires_stack_image_context_exclusion and
                        result.requires_prepared_synchronous_lifetime)
        self.assertFalse(result.execution_claim or result.consumer_proof)
        with self.assertRaises(FrozenInstanceError):result.target = 1
        mutable = bytearray(blob);snapshot = validate_mrs_island(mutable, **args)
        mutable[0] ^= 1
        self.assertEqual(snapshot.body_sha256, result.body_sha256)

    def test_every_payload_and_replacement_bit_is_checked(self):
        blob, args = fixture()
        for offset in range(64):
            for bit in range(8):
                changed = bytearray(blob);changed[offset] ^= 1 << bit
                with self.subTest(offset=offset, bit=bit):
                    self.assertIsNone(validate_mrs_island(changed, **args))
        for bit in range(32):
            changed = dict(args, replacement=args['replacement'] ^ (1 << bit))
            with self.subTest(replacement_bit=bit):
                self.assertIsNone(validate_mrs_island(blob, **changed))

    def test_every_supported_target_and_site_alignment_view(self):
        for target in range(31):
            if target == 18:
                continue
            for residue in (0, 4, 8, 12):
                blob, args = fixture(target=target, site=0x1000 + residue)
                with self.subTest(target=target, residue=residue):
                    self.assertEqual(validate_mrs_island(blob, **args).target, target)
        for target in (18, 31):
            blob, args = fixture(target=target)
            self.assertIsNone(validate_mrs_island(blob, **args))

    def test_generic_kind_old_literal_and_metadata_changes_refuse(self):
        blob, args = fixture()
        for offset, code, value in ((32, '<Q', args['entry'] + 4),
                                    (40, '<Q', args['island'] + 20),
                                    (48, '<I', 1), (52, '<I', 0), (52, '<I', 2),
                                    (56, '<Q', args['accessor'] + 4)):
            changed = bytearray(blob);struct.pack_into(code, changed, offset, value)
            with self.subTest(offset=offset, value=value):
                self.assertIsNone(validate_mrs_island(changed, **args))
        old = bytearray(blob)
        struct.pack_into('<I', old, 20, branch(args['island'] + 20, args['site'] + 4))
        struct.pack_into('<Q', old, 24, args['site'] + 4)
        self.assertIsNone(validate_mrs_island(old, **args))

    def test_signed_reach_and_tightened_return_edge(self):
        site = 0x20000000
        for island in (site - (1 << 27), site - 64, site + 64, site + (1 << 27) - 32):
            blob, args = fixture(site=site, island=island)
            with self.subTest(island=island):
                self.assertIsNotNone(validate_mrs_island(blob, **args))
        for island in (site - (1 << 27) - 16, site + (1 << 27) - 16,
                       site + (1 << 27), site + (1 << 28)):
            blob, args = fixture(site=site, island=island)
            with self.subTest(out_of_reach=island):
                self.assertIsNone(validate_mrs_island(blob, **args))
        # Old B-at20 admitted this exact boundary; B-at24 must not wrap it.
        self.assertEqual(branch(site + (1 << 27) - 16 + 20, site + 4), 0x16000000)

    def test_uint64_arithmetic_cannot_wrap_or_create_overlap(self):
        blob, args = fixture(site=UINT64_END - 0x2000, island=UINT64_END - 0x1000,
                             entry=UINT64_END - 4, accessor=UINT64_END - 8)
        self.assertIsNotNone(validate_mrs_island(blob, **args))
        base, arguments = fixture()
        for updates in ({'site': UINT64_END - 4}, {'island': UINT64_END - 64},
                        {'island': UINT64_END - 48}, {'site': UINT64_END},
                        {'island': UINT64_END}, {'site': arguments['island']},
                        {'site': arguments['island'] + 60}):
            with self.subTest(updates=updates):
                self.assertIsNone(validate_mrs_island(base, **dict(arguments, **updates)))
        zero_site, arguments = fixture(site=0, island=0x1000)
        self.assertIsNotNone(validate_mrs_island(zero_site, **arguments))

    def test_types_extents_alignment_and_nonnull_code_pointers_are_strict(self):
        blob, args = fixture()
        for value in (None, False, '', memoryview(blob), list(blob), object()):
            with self.subTest(blob_type=type(value).__name__):
                self.assertIsNone(validate_mrs_island(value, **args))
        for size in range(64):
            self.assertIsNone(validate_mrs_island(blob[:size], **args))
        self.assertIsNone(validate_mrs_island(blob + b'\0', **args))
        for field in args:
            for value in (None, True, -1, UINT64_END, '1'):
                with self.subTest(field=field, value=value):
                    self.assertIsNone(validate_mrs_island(blob, **dict(args, **{field: value})))
        for field, value in (('site', 0x1001), ('island', 0x2004), ('target', -1),
                             ('target', 32), ('replacement', 1 << 32),
                             ('entry', 0), ('accessor', 0), ('entry', 0x5002), ('accessor', 0x6002)):
            with self.subTest(field=field, value=value):
                self.assertIsNone(validate_mrs_island(blob, **dict(args, **{field: value})))


if __name__ == '__main__':
    unittest.main()
