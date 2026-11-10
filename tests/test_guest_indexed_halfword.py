# SPDX-License-Identifier: GPL-3.0-only
"""Byte/offset controls only; no executable-memory or TLS consumer proof."""
import dataclasses
import importlib.util
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from guest_indexed_halfword import (
    MAX_DISPLACEMENT,
    exact_halfword_displacement,
    recognize_indexed_halfword,
)


def encoded(operation='ldrh', option=3, shift=1, base=22, index=20, data=8):
    word = (0x78600800 if operation == 'ldrh' else 0x78200800)
    return (word | option << 13 | shift << 12 | base << 5 | index << 16 | data).to_bytes(4, 'little')


class IndexedHalfwordTests(unittest.TestCase):
    @unittest.skipUnless(importlib.util.find_spec('capstone'), 'requires pinned Capstone decoder')
    def test_independent_decoder_agrees_on_all_options_and_scales(self):
        import capstone
        self.assertEqual(capstone.__version__, '5.0.3')
        decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_LITTLE_ENDIAN)
        decoder.detail = True
        for op in ('ldrh', 'strh'):
            for option in (2, 3, 6, 7):
                for shift in (0, 1):
                    raw = encoded(op, option, shift)
                    profile = recognize_indexed_halfword(raw)
                    actual = next(decoder.disasm(raw, 0))
                    self.assertEqual(actual.mnemonic, profile.operation)
                    self.assertEqual(actual.reg_name(actual.operands[0].reg), profile.data_register)
                    self.assertEqual(actual.reg_name(actual.operands[1].mem.base), profile.base)
                    self.assertEqual(actual.reg_name(actual.operands[1].mem.index), profile.index)
                    self.assertEqual(actual.operands[1].shift.value, profile.shift)

    def test_actual_qtcore_words_and_frozen_descriptions(self):
        load = recognize_indexed_halfword(bytes.fromhex('c87a7478'))
        store = recognize_indexed_halfword(bytes.fromhex('c07a3578'))
        self.assertEqual((load.operation, load.base, load.index, load.data_register,
                          load.index_width, load.extension, load.shift, load.width),
                         ('ldrh', 'x22', 'x20', 'w8', 64, 'lsl', 1, 2))
        self.assertEqual((store.operation, store.base, store.index, store.data_register),
                         ('strh', 'x22', 'x21', 'w0'))
        with self.assertRaises(dataclasses.FrozenInstanceError):
            load.shift = 0

    def test_all_valid_options_scales_register_fields_and_zero_registers(self):
        for option, bits, extension in ((2, 32, 'uxtw'), (3, 64, 'lsl'),
                                        (6, 32, 'sxtw'), (7, 64, 'sxtx')):
            for operation in ('ldrh', 'strh'):
                for shift in (0, 1):
                    for register in range(32):
                        profile = recognize_indexed_halfword(encoded(operation, option, shift, register, register, register))
                        self.assertEqual((profile.index_width, profile.extension, profile.shift), (bits, extension, shift))
                        self.assertEqual(profile.base, 'sp' if register == 31 else f'x{register}')
                        self.assertEqual(profile.index, ('wzr' if bits == 32 else 'xzr') if register == 31 else f"{'w' if bits == 32 else 'x'}{register}")
                        self.assertEqual(profile.data_register, 'wzr' if register == 31 else f'w{register}')

    def test_invalid_options_fixed_bits_and_input_shapes_refuse(self):
        raw = encoded()
        variable = 0x001FF3FF  # Rm, option, S, Rn, Rt; fixed bit10/11 excluded.
        for bit in range(32):
            if variable & 1 << bit:
                continue
            word = int.from_bytes(raw, 'little') ^ (1 << bit)
            # Opc bit22 toggles LDRH to STRH, another intentionally valid form.
            if bit == 22:
                self.assertEqual(recognize_indexed_halfword(word.to_bytes(4, 'little')).operation, 'strh')
            else:
                self.assertIsNone(recognize_indexed_halfword(word.to_bytes(4, 'little')))
        for option in (0, 1, 4, 5):
            self.assertIsNone(recognize_indexed_halfword(encoded(option=option)))
        for raw in (None, 1, b'', b'\0' * 3, b'\0' * 5, bytearray(encoded())):
            self.assertIsNone(recognize_indexed_halfword(raw))

    def test_exact_signed_extension_and_unsigned_offsets(self):
        for option, bits in ((2, 32), (3, 64), (6, 32), (7, 64)):
            for shift in (0, 1):
                profile = recognize_indexed_halfword(encoded(option=option, shift=shift))
                for value in (0, 1, 19, MAX_DISPLACEMENT >> shift):
                    self.assertEqual(exact_halfword_displacement(profile, value), value << shift)
                self.assertIsNone(exact_halfword_displacement(profile, (MAX_DISPLACEMENT >> shift) + 1))
                high = (1 << bits) - 1
                self.assertEqual(exact_halfword_displacement(profile, high),
                                 -(1 << shift) if option in (6, 7) else None)
                if option in (6, 7):
                    value = (1 << bits) - (MAX_DISPLACEMENT >> shift)
                    self.assertEqual(exact_halfword_displacement(profile, value), -MAX_DISPLACEMENT)
                    self.assertIsNone(exact_halfword_displacement(profile, value - 1))
                for value in (-1, 1 << bits, True, 1.0, None):
                    self.assertIsNone(exact_halfword_displacement(profile, value))

    def test_shift_wrap_and_forged_profile_fields_refuse(self):
        unsigned = recognize_indexed_halfword(encoded(option=3))
        signed = recognize_indexed_halfword(encoded(option=7))
        self.assertIsNone(exact_halfword_displacement(unsigned, (1 << 64) - 1))
        self.assertIsNone(exact_halfword_displacement(signed, 1 << 62))
        self.assertIsNone(exact_halfword_displacement(signed, (1 << 63)))
        for replacement in ({'index_width': 32}, {'extension': 'bad'}, {'shift': 2}, {'shift': True}):
            self.assertIsNone(exact_halfword_displacement(dataclasses.replace(unsigned, **replacement), 0))
        self.assertIsNone(exact_halfword_displacement(None, 0))


if __name__ == '__main__':
    unittest.main()
