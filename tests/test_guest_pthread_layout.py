# SPDX-License-Identifier: GPL-3.0-only
"""Reject incomplete or impossible compiler output for private pthread facts."""

import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from generate_guest_pthread_layout import ATTR_FIELDS, FIELDS, parse_layout_ir, verified_header


def valid_values():
    values = {
        field: index * 8 if index < 3 else 24 + (index - 3) * 64
        for index, field in enumerate(FIELDS)
    }
    values.update({f"width_{field}": 8 for field in FIELDS})
    values.update(width_tid=4, width_attr=56, width_join_state=4)
    values.update(
        {f"attr_{field}": offset for field, offset in zip(ATTR_FIELDS, (0, 8, 16, 24, 32, 36, 40))}
    )
    values.update(
        {f"attr_width_{field}": width for field, width in zip(ATTR_FIELDS, (4, 8, 8, 8, 4, 4, 16))}
    )
    values.update(
        {
            f"attr_align_{field}": alignment
            for field, alignment in zip(ATTR_FIELDS, (4, 8, 8, 8, 4, 4, 1))
        }
    )
    values.update(
        size=4096,
        align=8,
        pointer_size=8,
        pid_size=4,
        handle_size=8,
        attr_size=56,
        join_not_joined=0,
        join_exited_not_joined=1,
        join_joined=2,
        join_detached=3,
        attr_flag_detached=1,
        attr_flag_joined=2,
        attr_flag_inherit=4,
        attr_flag_explicit=8,
    )
    return values


def ir(values):
    return "\n".join(f"@layout_{name} = constant i64 {value}" for name, value in values.items())


class PthreadLayoutTests(unittest.TestCase):
    def test_complete_facts(self):
        values = valid_values()
        self.assertEqual(parse_layout_ir(ir(values)), values)

    def test_missing_duplicate_and_unknown_refused(self):
        values = valid_values()
        for value in (
            ir({k: v for k, v in values.items() if k != "tid"}),
            ir(values) + "\n@layout_tid = constant i64 16",
            ir({**values, "unknown": 1}),
        ):
            with self.subTest(value=value[-60:]), self.assertRaises(ValueError):
                parse_layout_ir(value)

    def test_invalid_object_and_identity_layout_refused(self):
        values = valid_values()
        for field, replacement in (
            ("size", 0),
            ("size", 65544),
            ("align", 32),
            ("pointer_size", 4),
            ("pid_size", 8),
            ("handle_size", 4),
            ("next", 8),
            ("prev", 0),
            ("tid", 24),
            ("attr_size", 0),
            ("attr_size", 4096),
            ("stack_top", 4096),
            ("width_tid", 0),
            ("width_tid", 8),
            ("width_join_state", 8),
            ("width_attr", 57),
            ("width_next", 9),
            ("attr_stack_base", 4),
            ("attr_width_flags", 8),
            ("attr_align_stack_base", 1),
            ("attr___reserved", 41),
            ("join_detached", 2),
            ("attr_flag_detached", 0),
        ):
            with self.subTest(field=field), self.assertRaises(ValueError):
                parse_layout_ir(ir({**values, field: replacement}))

    def test_reordered_fields_refused(self):
        values = valid_values()
        values["join_state"] = values["attr"]
        with self.assertRaises(ValueError):
            parse_layout_ir(ir(values))

    def test_cached_source_integrity_and_symlink_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "fixture.h"
            data = b"controlled source\n"
            digest = hashlib.sha256(data).hexdigest()
            path.write_bytes(data)
            verified_header(root, "fixture.h", digest)
            path.write_bytes(b"changed\n")
            with self.assertRaises(ValueError):
                verified_header(root, "fixture.h", digest)
            path.unlink()
            original = root / "original.h"
            original.write_bytes(data)
            path.symlink_to(original)
            with self.assertRaises(ValueError):
                verified_header(root, "fixture.h", digest)


if __name__ == "__main__":
    unittest.main()
