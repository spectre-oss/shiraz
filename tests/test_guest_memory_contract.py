# SPDX-License-Identifier: GPL-3.0-only
"""Synthetic binding rejection checks, never native memory or TLS proof."""
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from guest_memory_contract import ABIS, byte_memory_binding
from test_guest_allocator_contract import malloc_binding
from test_guest_tpidr_audit import BindingElfFixture


class MemoryContractTests(unittest.TestCase):
    def test_exact_rows_and_versions(self):
        for abi, symbol in ABIS.items():
            for version in ("", "LIBC"):
                self.assertTrue(byte_memory_binding({**malloc_binding(), "abi": abi,
                                                     "symbol": symbol, "version": version}))
        self.assertFalse(byte_memory_binding({**malloc_binding(), "symbol": "memcpy"}))
        self.assertFalse(byte_memory_binding({"abi": "memory/byte-copy-v2"}))

    def test_mutated_native_tuple_rejected(self):
        for abi, symbol in ABIS.items():
            row = {**malloc_binding(), "abi": abi, "symbol": symbol}
            for key, value in (("symbol", "memset"), ("version", "LIBC_O"),
                               ("provider", "other.so"), ("kind", "object"),
                               ("target", 0), ("provider_elf_sha256", "a" * 64),
                               ("addend", False), ("addend", 4), ("relocation_type", True),
                               ("relocation_type", 257), ("nonreturning", True),
                               ("nonreturning", 0), ("native_target", None)):
                with self.subTest(abi=abi, key=key), self.assertRaises(ValueError):
                    byte_memory_binding({**row, key: value})

    def test_resolver_trust_never_supplies_caller_range_proof(self):
        for abi, symbol in ABIS.items():
            fixture = BindingElfFixture()
            encoded = symbol.encode() + b"\0"
            fixture.data[0x1301:0x1301 + len(encoded)] = encoded
            with tempfile.TemporaryDirectory() as directory:
                bridge = Path(directory) / "bridge.so"
                bridge.write_bytes(b"synthetic bridge identity, never native execution")
                proof = fixture.proof()
                proof["modules"][0]["bindings"] = [
                    {**malloc_binding(), "abi": abi, "symbol": symbol}]
                proof["native_bridge"] = {
                    "path": str(bridge.resolve()),
                    "sha256": hashlib.sha256(bridge.read_bytes()).hexdigest(),
                    "verification": "observed-before-and-after-process"}
                self.assertEqual(fixture.metadata(proof).resolve(0x1500)["reason"],
                                 "byte-memory-contract-untrusted")
                selected = fixture.metadata(proof, bridge).resolve(0x1500)
                self.assertEqual(selected["abi"], abi)
                self.assertEqual(selected["reason"], "explicit-first-party-byte-memory-contract")
                self.assertEqual(selected["effect"], "byte-memory")
                self.assertEqual(selected["read_slot"], 0x1500)
                self.assertIsNone(selected["target"])
                proof["modules"][0]["bindings"][0]["relocation_type"] = 1025
                self.assertEqual(fixture.metadata(proof, bridge).resolve(0x1500)["reason"],
                                 "byte-memory-binding-mismatch")
