# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic file/profile integration; never app execution or TLS acceptance."""
import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from check_guest_mrs_accessor import check_accessor
from test_guest_cached_self_record import fixture as macho_fixture
from test_guest_mrs_access import fixture as code_fixture


def image_fixture():
    image, _ = macho_fixture()
    struct.pack_into('<Q', image, 32 + 72 + 40, 220)  # __text section extent
    struct.pack_into('<Q', image, 32 + 152 + 32, 0x78000)  # DATA vmsize
    struct.pack_into('<Q', image, 32 + 152 + 72 + 24, 0x90000)  # LINKEDIT vmaddr
    body = struct.pack('<55I', *code_fixture(entry=0x1000))
    image[0x1000:0x1000 + len(body)] = body
    strings = b'\0_shiraz_guest_tls_access\0'
    image[0x4010:0x4030] = strings.ljust(32, b'\0')
    return image


class MrsAccessorCodeTests(unittest.TestCase):
    def check(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bridge'
            path.write_bytes(data)
            return check_accessor(path)

    def test_exact_file_identity_profile_and_nonclaims(self):
        image = image_fixture();result = self.check(image)
        self.assertEqual(result['image_sha256'], hashlib.sha256(image).hexdigest())
        self.assertEqual(result['effects']['normal_path_calls'], 0)
        self.assertEqual(len(result['effects']['tables']), 5)
        self.assertEqual(len(result['effects']['failure_calls']), 4)
        self.assertFalse(result['execution_claim'] or result['consumer_proof'])
        self.assertFalse(result['effects']['failure_nonreturning_verified'])

    def test_code_identity_and_table_ownership_changes_refuse(self):
        image = image_fixture()
        for offset, value in ((0x1000 + 8, 0), (0x4011, ord('x')),
                              (32 + 152 + 60, 0)):
            changed = bytearray(image);changed[offset] = value
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                self.check(changed)

    def test_empty_and_truncated_images_refuse(self):
        for image in (b'', image_fixture()[:0x1000], image_fixture()[:0x4000]):
            with self.subTest(length=len(image)), self.assertRaises(ValueError):
                self.check(image)


if __name__ == '__main__':
    unittest.main()
