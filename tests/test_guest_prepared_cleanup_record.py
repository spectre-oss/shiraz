# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic snapshot/file consistency checks, not runtime publication proof."""
import copy
import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_prepared_cleanup import BODY_BYTES, TABLE_BYTES
from guest_prepared_cleanup_record import ROLES, validate_prepared_cleanup_record
from test_guest_prepared_cleanup_file import image_fixture


def record_fixture(image=None):
    image = image_fixture() if image is None else image
    return {
        'profile': 1, 'verification': 'mapped-first-party-code-and-cache-ranges',
        'execution_claim': False, 'entry_offset': 0x1000,
        'body_hex': image[0x1000:0x1000+BODY_BYTES].hex(),
        'tables': [{'role': role, 'offset': offset, 'bytes': TABLE_BYTES}
                   for role, offset in zip(ROLES, (0x20000, 0x30000, 0x40000, 0x50000, 0x60000))],
        'stack_exclusion_profile': 1,
    }


class PreparedCleanupRecordTests(unittest.TestCase):
    def test_optional_absence_and_schema_only_never_supply_effects(self):
        self.assertIsNone(validate_prepared_cleanup_record(None))
        self.assertIsNone(validate_prepared_cleanup_record(None, b'invalid'))
        self.assertIsNone(validate_prepared_cleanup_record(record_fixture()))
        for value in (False, 0, [], 'record'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(value)

    def test_valid_zero_and_high_image_bases_match_frozen_facts(self):
        for base in (0, 0x100000000):
            image = image_fixture(base);record = record_fixture(image)
            checked = validate_prepared_cleanup_record(record, image)
            self.assertEqual(checked.record.entry_offset, 0x1000)
            self.assertEqual(checked.effects.entry, base+0x1000)
            self.assertEqual([(t.role, t.address, t.byte_count) for t in checked.effects.tables],
                             [(t['role'], base+t['offset'], t['bytes']) for t in record['tables']])
            self.assertFalse(checked.effects.table_identity_verified)
            self.assertFalse(checked.effects.metadata_publication_verified)
            self.assertFalse(checked.effects.lifetime_verified)
            self.assertFalse(checked.effects.native_memory_purity_verified)
            self.assertFalse(checked.effects.consumer_proof)
            self.assertTrue(checked.effects.requires_matching_runtime_tables)
            with self.assertRaises(FrozenInstanceError):checked.record.entry_offset = 4
            with self.assertRaises(FrozenInstanceError):checked.record.tables[0].offset = 8
            record['tables'][0]['offset'] = 8
            self.assertEqual(checked.record.tables[0].offset, 0x20000)

    def test_exact_record_fields_and_numeric_types_are_required_without_bytes(self):
        original = record_fixture()
        for key in original:
            missing = copy.deepcopy(original);del missing[key]
            extra = copy.deepcopy(original);extra['unknown'] = False
            with self.subTest(missing=key), self.assertRaises(ValueError):validate_prepared_cleanup_record(missing)
            with self.subTest(extra=key), self.assertRaises(ValueError):validate_prepared_cleanup_record(extra)
        variants = [('profile', True), ('profile', 2), ('stack_exclusion_profile', True),
                    ('stack_exclusion_profile', 0), ('execution_claim', 0), ('execution_claim', True),
                    ('verification', 'file-only'), ('verification', None),
                    ('entry_offset', True), ('entry_offset', 4.0), ('entry_offset', -4),
                    ('entry_offset', 2), ('entry_offset', (1 << 64)-BODY_BYTES+4)]
        for key, value in variants:
            record = copy.deepcopy(original);record[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(record)

    def test_body_hex_is_bounded_canonical_lowercase(self):
        original = record_fixture();body = original['body_hex']
        for value in (None, body.encode(), 0, body[:-1], body+'00', body.upper(), 'g'+body[1:]):
            record = copy.deepcopy(original);record['body_hex'] = value
            with self.subTest(value_type=type(value)), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(record)

    def test_table_count_order_schema_and_extents_are_required(self):
        original = record_fixture()
        bad_rows = [None, (), original['tables'][:-1], original['tables']+original['tables'][:1],
                    list(reversed(original['tables']))]
        for rows in bad_rows:
            record = copy.deepcopy(original);record['tables'] = rows
            with self.subTest(rows=rows), self.assertRaises(ValueError):validate_prepared_cleanup_record(record)
        for key, value in [('role', 'keys'), ('role', False), ('bytes', True), ('bytes', TABLE_BYTES-8),
                           ('offset', True), ('offset', -8), ('offset', 0x20004),
                           ('offset', (1 << 64)-TABLE_BYTES+8)]:
            record = copy.deepcopy(original);record['tables'][0][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(record)
        for change in ('missing', 'extra', 'not-object'):
            record = copy.deepcopy(original)
            if change == 'missing':del record['tables'][0]['bytes']
            elif change == 'extra':record['tables'][0]['unknown'] = 0
            else:record['tables'][0] = []
            with self.subTest(change=change), self.assertRaises(ValueError):validate_prepared_cleanup_record(record)
        for offset in (0x1000, 0x20000, 0x20008):
            record = copy.deepcopy(original);record['tables'][1]['offset'] = offset
            with self.subTest(overlap=offset), self.assertRaises(ValueError):validate_prepared_cleanup_record(record)
        adjacent = copy.deepcopy(original);adjacent['tables'][1]['offset'] = 0x20000+TABLE_BYTES
        self.assertIsNone(validate_prepared_cleanup_record(adjacent))

    def test_body_matching_does_not_replace_instruction_validation(self):
        image = image_fixture();record = record_fixture(image)
        record['body_hex'] = '00'+record['body_hex'][2:]
        with self.assertRaisesRegex(ValueError, 'mapped/file body mismatch'):
            validate_prepared_cleanup_record(record, image)
        image[0x1000] ^= 1;record = record_fixture(image)
        with self.assertRaisesRegex(ValueError, 'complete body/table effects'):
            validate_prepared_cleanup_record(record, image)

    def test_readable_table_mismatch_and_rebased_addresses_refuse(self):
        image = image_fixture();record = record_fixture(image);record['tables'][0]['offset'] += 8
        with self.assertRaisesRegex(ValueError, 'instruction/record table mismatch'):
            validate_prepared_cleanup_record(record, image)
        high = image_fixture(0x100000000);record = record_fixture(high)
        record['entry_offset'] += 0x100000000
        with self.assertRaisesRegex(ValueError, 'independent symbol'):
            validate_prepared_cleanup_record(record, high)
        record = record_fixture(high)
        for row in record['tables']:row['offset'] += 0x100000000
        with self.assertRaisesRegex(ValueError, 'instruction/record table mismatch'):
            validate_prepared_cleanup_record(record, high)
        # A matching shifted file/record is consistency only. Producer trust,
        # actual array semantics and publication cannot be inferred from bytes.
        shifted = image_fixture(owner_delta=8);record = record_fixture(shifted)
        record['tables'][0]['offset'] += 8
        checked = validate_prepared_cleanup_record(record, shifted)
        self.assertFalse(checked.effects.table_identity_verified)
        self.assertFalse(checked.effects.consumer_proof)

    def test_normalization_overflow_is_refused_with_real_high_base_image(self):
        image = image_fixture(0x100000000);record = record_fixture(image)
        record['entry_offset'] = (1 << 64)-BODY_BYTES
        with self.assertRaisesRegex(ValueError, 'independent symbol'):
            validate_prepared_cleanup_record(record, image)
        record = record_fixture(image);record['tables'][-1]['offset'] = (1 << 64)-TABLE_BYTES
        with self.assertRaisesRegex(ValueError, 'normalization overflow'):
            validate_prepared_cleanup_record(record, image)

    def test_symbol_ownership_permissions_and_truncated_bytes_refuse(self):
        image = image_fixture();record = record_fixture(image)
        for offset in (0x4004, 0x4021, 32+152+60):
            changed = bytearray(image);changed[offset] = 0
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(record, changed)
        for data in (b'', image[:32], image[:0x4000]):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                validate_prepared_cleanup_record(record, data)
        wrong_entry = record_fixture(image);wrong_entry['entry_offset'] += 4
        with self.assertRaisesRegex(ValueError, 'independent symbol'):
            validate_prepared_cleanup_record(wrong_entry, image)


if __name__ == '__main__':
    unittest.main()
