# SPDX-License-Identifier: GPL-3.0-only
"""Bounded read-only decoding of a recorded guest ELF namespace.

Addresses above the root module's range are analysis tokens, never native
addresses or a reconstruction of runtime load biases. No host image is decoded.
"""

import hashlib
import json
from dataclasses import replace
from pathlib import Path

import guest_cross_binding

STRIDE = 1 << 40
MAX_MODULES = 128
MAX_FILE = 256 << 20
MAX_TOTAL = 512 << 20
MAX_CROSS_SYMBOL_WORK = 2_000_000


class NamespaceDecoder:
    def __init__(self, root_path, report_path, directory, decoder_factory, provider_bridge=None):
        directory = Path(directory).resolve(strict=True)
        root_path = Path(root_path).resolve(strict=True)
        report_path = Path(report_path)
        if not directory.is_dir() or not 0 < report_path.stat().st_size <= 64 << 20:
            raise ValueError("guest namespace directory/report bound")
        with report_path.open("rb") as stream:
            report_bytes = stream.read((64 << 20) + 1)
        if len(report_bytes) > 64 << 20:
            raise ValueError("guest namespace report bound")
        report = json.loads(report_bytes)
        if (type(report.get("schema")) is not int or report["schema"] != 1
                or report.get("namespace") != "nativebridge"
                or report.get("snapshot") != "post-link-before-open"):
            raise ValueError("guest namespace needs actual NativeBridge binding evidence")
        modules = report.get("modules")
        if not isinstance(modules, list) or not 0 < len(modules) <= MAX_MODULES:
            raise ValueError("guest namespace module bound")
        names, paths, hashes, total = set(), {}, {}, 0
        for module in modules:
            if not isinstance(module, dict):
                raise TypeError("invalid guest namespace module")
            name = module.get("name")
            if (not isinstance(name, str) or not name or len(name) > 4096
                    or Path(name).name != name or name in {".", ".."} or name in names):
                raise ValueError("duplicate/unsafe guest namespace name")
            names.add(name)
            path = (directory / name).resolve(strict=True)
            recorded = module.get("file")
            if (not isinstance(recorded, str) or len(recorded) > 4096
                    or path.parent != directory or not path.is_file()
                    or Path(recorded).resolve(strict=True) != path):
                raise ValueError("guest namespace file escapes explicit directory")
            if not 0 < path.stat().st_size <= MAX_FILE:
                raise ValueError("guest namespace ELF file bound")
            with path.open("rb") as stream:
                data = stream.read(MAX_FILE + 1)
            total += len(data)
            if len(data) > MAX_FILE or total > MAX_TOTAL:
                raise ValueError("guest namespace aggregate byte bound")
            digest = hashlib.sha256(data).hexdigest()
            if module.get("elf_sha256") != digest:
                raise ValueError("guest namespace ELF hash mismatch")
            paths[name], hashes[name] = path, digest
        if root_path.parent != directory or paths.get(root_path.name) != root_path:
            raise ValueError("root ELF is not the recorded explicit namespace file")
        order = [root_path.name, *sorted(names - {root_path.name})]
        self.names, self.by_name, self.decoders = order, {}, []
        proof_hash = hashlib.sha256(report_bytes).hexdigest()
        for index, name in enumerate(order):
            decoder = decoder_factory(paths[name], report_path, provider_bridge)
            if (hashlib.sha256(decoder.data).hexdigest() != hashes[name]
                    or decoder.bindings is None or decoder.bindings.name != name
                    or decoder.bindings.proof_hash != proof_hash):
                raise ValueError("guest namespace decoder identity/metadata mismatch")
            if any(not 0 <= begin <= end < STRIDE for begin, end, *_ in decoder.bindings.loads):
                raise ValueError("guest namespace image exceeds analysis address slot")
            self.by_name[name] = index
            self.decoders.append(decoder)
        self.root = self.decoders[0]
        self.return_auth_profile = getattr(self.root, "return_auth_profile", None)
        self.data, self.bindings = self.root.data, self.root.bindings
        self.binding_metadata_unavailable = self.root.binding_metadata_unavailable
        self.address_space = {
            "kind": "synthetic-per-module-analysis-tokens-not-native-addresses",
            "stride": hex(STRIDE),
            "limits": {"modules": MAX_MODULES, "elf_bytes": MAX_TOTAL,
                       "cross_symbol_work": MAX_CROSS_SYMBOL_WORK},
            "modules": [{"name": name, "base": hex(index * STRIDE), "elf_sha256": hashes[name]}
                        for index, name in enumerate(order)],
        }
        self.identity = {
            **self.root.identity,
            "namespace_decoder_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "cross_binding_sha256": hashlib.sha256(Path(guest_cross_binding.__file__).read_bytes()).hexdigest(),
            "guest_namespace": self.address_space,
        }
        self.cache, self.plt_cache = {}, {}
        self.cross_symbol_work = 0

    def locate(self, address):
        if type(address) is not int or not 0 <= address < len(self.decoders) * STRIDE:
            return None
        index, local = divmod(address, STRIDE)
        return index, local, self.decoders[index]

    def __call__(self, address):
        if address in self.cache:
            return self.cache[address]
        located = self.locate(address)
        if located is None:
            return None
        index, local, decoder = located
        item = decoder(local)
        if item is None:
            return None
        operands = []
        branch = item.mnemonic in {"b", "bl", "cbz", "cbnz", "tbz", "tbnz"} or item.mnemonic.startswith("b.")
        literal = item.mnemonic in {"ldr", "ldrsw", "prfm"} and len(item.operands) == 2
        for position, operand in enumerate(item.operands):
            relative = ((branch and position == len(item.operands) - 1)
                        or (item.mnemonic in {"adr", "adrp"} and position == 1)
                        or (literal and position == 1))
            if relative and operand.kind == "imm":
                if type(operand.value) is not int or not 0 <= operand.value < STRIDE:
                    operand = replace(operand, kind="other", value=0)
                else:
                    operand = replace(operand, value=index * STRIDE + operand.value)
            operands.append(operand)
        result = replace(item, address=address, operands=tuple(operands))
        if len(self.cache) < 1 << 20:
            self.cache[address] = result
        return result

    def word(self, address):
        located = self.locate(address)
        return located[2].word(located[1]) if located else None

    def sites(self):
        return self.root.sites()

    def owned_image_data(self, address, width):
        located = self.locate(address)
        if located is None or type(width) is not int or width <= 0 or located[1] + width > STRIDE:
            return False
        return located[2].owned_image_data(located[1], width)

    def mrs_stack_window(self, address):
        located = self.locate(address)
        if located is None:
            return None
        index, local, decoder = located
        lookup = getattr(decoder, "mrs_stack_window", None)
        record = lookup(local) if lookup else None
        if record is None:
            return None
        return {**record, "analysis_site": hex(address), "module": self.names[index]}

    def adjust_image_address(self, origin, delta):
        located = self.locate(origin)
        if located is None or type(delta) is not int:
            return None
        index, local, _ = located
        local += delta
        return index * STRIDE + local if 0 <= local < STRIDE else None

    def atomic(self, address, method):
        located = self.locate(address)
        if located is None:
            return None
        index, local, decoder = located
        summary = getattr(decoder, method)(local)
        if summary is None:
            return None
        bias = index * STRIDE
        if not 0 <= summary.flag_address < STRIDE:
            return None
        return replace(summary, entry=address, flag_address=bias + summary.flag_address,
                       paths=tuple(replace(path, ret_pc=bias + path.ret_pc) for path in summary.paths))

    def atomic32(self, address):
        return self.atomic(address, "atomic32")

    def cas64(self, address):
        return self.atomic(address, "cas64")

    def byte_jump_table(self, address):
        located = self.locate(address)
        if located is None:
            return None
        index, local, decoder = located
        lookup = getattr(decoder, "byte_jump_table", None)
        summary = lookup(local) if lookup else None
        if summary is None:
            return None
        addresses = (summary.entry, summary.default_target, summary.table_address, summary.branch_base, *summary.targets)
        if (any(type(value) is not int or not 0 <= value < STRIDE for value in addresses)
                or summary.entry + summary.body_size > STRIDE
                or summary.table_address + len(summary.table_bytes) > STRIDE):
            return None
        bias = index * STRIDE
        return replace(summary, entry=address, default_target=bias + summary.default_target,
                       table_address=bias + summary.table_address, branch_base=bias + summary.branch_base,
                       targets=tuple(bias + target for target in summary.targets))

    def caller_frame(self, address):
        located = self.locate(address)
        if located is None:
            return None, {"status": "unknown", "reason": "caller-outside-guest-namespace"}
        index, local, decoder = located
        state, evidence = decoder.caller_frame(local)
        evidence = {**evidence, "module": self.names[index]}
        if "entry" in evidence:
            evidence["entry_relative"] = evidence["entry"]
            evidence["entry"] = hex(index * STRIDE + int(evidence["entry"], 16))
        if state is not None:
            for mapping in (state.image_addresses, state.stack_images):
                for key, value in list(mapping.items()):
                    if type(value) is int and 0 <= value < STRIDE:
                        mapping[key] = index * STRIDE + value
                    else:
                        del mapping[key]
        return state, evidence

    def plt(self, address):
        if address in self.plt_cache:
            return self.plt_cache[address]
        located = self.locate(address)
        if located is None:
            return None
        index, local, decoder = located
        result = decoder.plt(local)
        if result is None:
            return None
        result = {**result, "source_module": self.names[index]}
        target_index = index
        if result.get("reason") == "external-or-preempted-namespace-binding":
            slot = decoder.bindings.number(result["slot"])
            proof = decoder.bindings.proof[slot]
            provider_index = self.by_name.get(proof["provider"])
            if provider_index is not None:
                provider = self.decoders[provider_index].bindings
                cost = provider.symbol_count
                if type(cost) is not int or cost < 1:
                    raise ValueError("invalid cross-binding symbol work cost")
                if self.cross_symbol_work + cost > MAX_CROSS_SYMBOL_WORK:
                    result = {**result, "reason": "cross-binding-symbol-work-budget"}
                else:
                    self.cross_symbol_work += cost
                    verified = guest_cross_binding.verify_guest_target(decoder.bindings, slot, provider)
                    result = {**result, **verified}
                    target_index = provider_index
        target = result.get("target")
        if target is not None:
            if type(target) is not int or not 0 <= target < STRIDE:
                raise ValueError("guest namespace target outside analysis slot")
            result = {**result, "target_relative": hex(target), "target_module": self.names[target_index],
                      "target": target_index * STRIDE + target}
        if len(self.plt_cache) < 1 << 20:
            self.plt_cache[address] = result
        return result
