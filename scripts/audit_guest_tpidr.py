#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
# /// script
# requires-python = ">=3.11"
# dependencies = ["capstone==5.0.3"]
# ///
"""Bounded diagnostic TPIDR reaching definitions; never modifies an ELF.

Run with UV_CACHE_DIR=.deps/uv-cache uv run --no-project
scripts/audit_guest_tpidr.py ELF --output REPORT.json.
This candidate audit supplements the loader's independent code provenance pass.
Guard-only describes understood reachable consumers in a bounded candidate context,
including reseeded TP reads reached on those paths. It is not general ELF TLS
support, complete caller provenance, or proof that scanned words are code.
"""

import argparse
import hashlib
import json
import struct
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

import guest_callee_effects

MAX_BYTES = 256 << 20
MAX_SITES = 131072
MAX_STEPS = 8192
MAX_CONTEXTS = 4096
MAX_CALL_DEPTH = 4
MAX_STACK_SLOTS = 256
MAX_VALUES = 8
MAX_TOTAL_STEPS = 2_000_000
MAX_CALLER_PRELUDE = 64
CLEAN = frozenset({None})
ORDERED_LOADS = frozenset({"ldar", "ldarb", "ldarh"})
ORDERED_STORES = frozenset({"stlr", "stlrb", "stlrh"})


def reg(name):
    if name in {"fp", "lr", "wsp"}:
        return {"fp": "x29", "lr": "x30", "wsp": "sp"}[name]
    return "x" + name[1:] if name.startswith("w") else name


def full_width_gpr(name):
    return (
        isinstance(name, str)
        and not name.startswith("w")
        and reg(name) in {f"x{index}" for index in range(31)}
    )


def memory_width(mnemonic, destination):
    # Sign-extending word loads write X registers but read only four bytes.
    if mnemonic in {"ldrsw", "ldursw", "ldpsw"}:
        return 4
    if mnemonic.startswith(("ldr", "str", "ldur", "stur")) or mnemonic in (
        ORDERED_LOADS | ORDERED_STORES
    ):
        if mnemonic.endswith("b"):
            return 1
        if mnemonic.endswith("h"):
            return 2
    if destination.startswith(("w", "s")):
        return 4
    if destination.startswith("q"):
        return 16
    return 8


def valid_ordered_scalar_transfer(instruction):
    """Validate only scalar LDAR/STLR forms, never exclusive or RMW atomics.

    Their register/memory data flow matches an ordinary load/store for this
    diagnostic. Acquire/release ordering is not a TLS-use or concurrency proof.
    In particular byte/halfword transfers use W registers but touch 1/2 bytes.
    """
    operands = instruction.operands
    if (
        len(operands) != 2
        or operands[0].kind != "reg"
        or operands[1].kind != "mem"
        or instruction.writeback
        or instruction.post_index
        or operands[1].index
        or operands[1].displacement
    ):
        return False
    destination = operands[0].value
    normalized = reg(destination)
    scalar = normalized in {"xzr", *(f"x{index}" for index in range(31))}
    base = reg(operands[1].base)
    address = not operands[1].base.startswith("w") and base in {
        "sp",
        *(f"x{index}" for index in range(31)),
    }
    if not scalar or not address:
        return False
    if instruction.mnemonic.endswith(("b", "h")) and not destination.startswith("w"):
        return False
    return instruction.width == memory_width(instruction.mnemonic, destination)


@dataclass(frozen=True)
class Operand:
    kind: str
    value: str | int = 0
    base: str = ""
    index: str = ""
    displacement: int = 0


@dataclass(frozen=True)
class Instruction:
    address: int
    mnemonic: str
    operands: tuple = ()
    reads: tuple = ()
    writes: tuple = ()
    writeback: bool = False
    post_index: int = 0
    width: int = 8
    text: str = ""
    tpidr_read: bool = False
    control_transfer: bool = False


@dataclass
class State:
    registers: dict = field(default_factory=dict)
    stack: dict = field(default_factory=dict)
    sp: int = 0
    fp: int | None = None
    # Separate from TP offsets: None still means an unknown address, never disjoint.
    aliases: dict = field(default_factory=dict)
    stack_aliases: dict = field(default_factory=dict)
    return_values: dict = field(default_factory=dict)
    stack_returns: dict = field(default_factory=dict)
    # Exact ELF-relative PC-derived addresses, never native stack offsets.
    # Unknown pointer contents loaded from image data do not acquire a fact.
    image_addresses: dict = field(default_factory=dict)
    stack_images: dict = field(default_factory=dict)

    def copy(self):
        return State(
            dict(self.registers),
            dict(self.stack),
            self.sp,
            self.fp,
            dict(self.aliases),
            dict(self.stack_aliases),
            dict(self.return_values),
            dict(self.stack_returns),
            dict(self.image_addresses),
            dict(self.stack_images),
        )

    def fingerprint(self):
        # Retain explicit CLEAN entries too: they affect the bounded spill-slot
        # accounting. Frozenset values make ordering inside each lattice value
        # immaterial, while sorted keys canonicalize dictionary insertion order.
        return (
            self.sp,
            self.fp,
            *(
                tuple(sorted(mapping.items()))
                for mapping in (
                    self.registers,
                    self.stack,
                    self.aliases,
                    self.stack_aliases,
                    self.return_values,
                    self.stack_returns,
                    self.image_addresses,
                    self.stack_images,
                )
            ),
        )

    def control(self, name):
        return self.return_values.get(reg(name), CLEAN)

    def set_control(self, name, value):
        name = reg(name)
        if name not in {"xzr", "wzr", "sp"}:
            self.return_values[name] = value

    def value(self, name):
        return self.registers.get(reg(name), CLEAN)

    def alias(self, name):
        name = reg(name)
        if name == "sp":
            return frozenset({self.sp})
        if name == "x29" and self.fp is not None:
            return frozenset({self.fp})
        return self.aliases.get(name, CLEAN)

    def set_alias(self, name, value):
        name = reg(name)
        if name not in {"xzr", "wzr", "sp"}:
            self.aliases[name] = value

    def set(self, name, value):
        name = reg(name)
        if name not in {"xzr", "wzr", "sp"}:
            self.registers[name] = value
            self.return_values[name] = CLEAN
            self.image_addresses.pop(name, None)

    def image(self, name):
        return self.image_addresses.get(reg(name)) if full_width_gpr(name) else None

    def set_image(self, name, value):
        normalized = reg(name)
        self.image_addresses.pop(normalized, None)
        if full_width_gpr(name) and type(value) is int and 0 <= value < 1 << 64:
            self.image_addresses[normalized] = value


def tainted(value):
    return any(item is not None for item in value)


def caller_prologue(decode, root, caller):
    """Model only a straight-line prologue from an independently proved entry.

    The caller must supply the entry metadata proof. This routine never searches
    backward from a BL or infers that an adjacent instruction was executed.
    """
    if root & 3 or caller & 3 or not 0 <= caller - root <= 4 * MAX_CALLER_PRELUDE:
        raise MetadataUnavailable("caller prelude alignment/instruction bound")
    state = State()

    def assign(name, alias):
        state.set_alias(name, alias)
        if reg(name) == "x29":
            state.fp = (
                next(iter(alias)) if len(alias) == 1 and None not in alias else None
            )

    for pc in range(root, caller, 4):
        item = decode(pc)
        if item is None:
            raise MetadataUnavailable("caller prelude undecodable")
        op, operands = item.mnemonic, item.operands
        if op in {"nop", "bti", "paciasp", "pacibsp"}:
            continue
        if (
            op == "mov"
            and len(operands) == 2
            and all(x.kind == "reg" for x in operands)
        ):
            destination, source = operands
            if destination.value.startswith("w") or source.value.startswith("w"):
                raise MetadataUnavailable("caller prelude narrowed register move")
            alias = state.alias(source.value)
            if destination.value == "sp":
                if len(alias) != 1 or None in alias:
                    raise MetadataUnavailable("caller prelude unknown SP move")
                state.sp = next(iter(alias))
            else:
                assign(destination.value, alias)
            continue
        if (
            op in {"add", "sub"}
            and len(operands) == 3
            and operands[0].kind == operands[1].kind == "reg"
            and operands[2].kind == "imm"
        ):
            destination, source, immediate = operands
            if destination.value.startswith("w") or source.value.startswith("w"):
                raise MetadataUnavailable("caller prelude narrowed register arithmetic")
            delta = immediate.value * (1 if op == "add" else -1)
            alias = frozenset(
                None if value is None else value + delta
                for value in state.alias(source.value)
            )
            if any(value is not None and abs(value) > 1 << 20 for value in alias):
                raise MetadataUnavailable("caller prelude stack offset bound")
            if destination.value == "sp":
                if len(alias) != 1 or None in alias:
                    raise MetadataUnavailable("caller prelude unknown SP arithmetic")
                state.sp = next(iter(alias))
            else:
                assign(destination.value, alias)
            continue
        if op in {"stp", "str", "stur"}:
            memory = [x for x in operands if x.kind == "mem"]
            data = [x for x in operands if x.kind == "reg"]
            if (
                len(memory) != 1
                or len(data) != (2 if op == "stp" else 1)
                or item.width != 8
                or memory[0].index
                or any(
                    not (reg(x.value).startswith("x") or x.value.startswith("d"))
                    for x in data
                )
            ):
                raise MetadataUnavailable("caller prelude unsupported stack store")
            memory = memory[0]
            base = state.alias(memory.base)
            if len(base) != 1 or None in base:
                raise MetadataUnavailable("caller prelude unknown store address")
            base = next(iter(base))
            for index, source in enumerate(data):
                slot = base + memory.displacement + 8 * index
                if (
                    slot & 7
                    or abs(slot) > 1 << 20
                    or len(state.stack) >= MAX_STACK_SLOTS
                ):
                    raise MetadataUnavailable("caller prelude stack slot bound")
                state.stack[slot] = CLEAN
                state.stack_aliases[slot] = state.alias(source.value)
            if item.writeback:
                if memory.base != "sp":
                    raise MetadataUnavailable("caller prelude non-SP writeback")
                state.sp += item.post_index or memory.displacement
            continue
        # Calls, branches, loads and arbitrary arithmetic cannot be silently
        # skipped: they could change SP/FP, saved slots or entry reachability.
        raise MetadataUnavailable("caller prelude unsupported instruction:" + op)
    if state.sp & 15:
        raise MetadataUnavailable("caller prelude unaligned call SP")
    return state


class Audit:
    def __init__(self, decode, *, max_steps=MAX_STEPS, max_contexts=MAX_CONTEXTS):
        self.decode = decode
        self.max_steps = max_steps
        self.max_contexts = max_contexts

    def site(self, address, caller=None):
        reasons, accesses, callees = set(), set(), set()
        plt_bindings = {}
        callee_effects = {}
        owned_image_accesses = set()
        states = {}
        pending = deque()
        steps = 0

        def problem(pc, reason):
            reasons.add((pc, reason))

        def observe_owned_image_access(pc, start, width, load, store):
            if (
                type(start) is not int
                or type(width) is not int
                or width <= 0
                or not 0 <= start < 1 << 64
                or start + width > 1 << 64
                or not hasattr(self.decode, "owned_image_data")
                or self.decode.owned_image_data(start, width) is not True
            ):
                return
            owned_image_accesses.add((pc, start, width, load, store))
            # Ownership is not stack disjointness. This audit starts at an
            # arbitrary MRS with no independently proved native-stack entry;
            # earlier guest code could have pivoted SP into this very image.
            # Retain the observation without suppressing alias uncertainty.

        def enqueue(pc, returns, state):
            # A union at a join loses correlations between register values,
            # spill contents and stack addresses. Keep each exact abstract state
            # separately; an unsafe path remains a sticky problem for the site.
            for mapping in (
                state.registers,
                state.stack,
                state.aliases,
                state.stack_aliases,
                state.return_values,
                state.stack_returns,
            ):
                if any(len(value) > MAX_VALUES for value in mapping.values()):
                    problem(pc, "value-set-limit")
                    return
            key = (pc, returns, state.fingerprint())
            if key in states:
                return
            if len(states) >= self.max_contexts:
                problem(pc, "context-limit")
                return
            states[key] = state.copy()
            pending.append(key)

        first = self.decode(address)
        if first is None or first.mnemonic != "mrs" or not first.operands:
            return {
                "site": hex(address),
                "classification": "unknown",
                "reasons": ["not-decoded-mrs"],
            }
        initial = State()
        caller_frame = None
        returns = ()
        if caller is not None:
            call = self.decode(caller)
            if (
                call is None
                or call.mnemonic != "bl"
                or not call.operands
                or call.operands[0] != Operand("imm", address)
            ):
                raise ValueError("caller must be an exact direct BL to this MRS entry")
            if hasattr(self.decode, "caller_frame"):
                prepared, caller_frame = self.decode.caller_frame(caller)
                if prepared is not None:
                    initial = prepared
                else:
                    problem(caller, "caller-prelude:" + caller_frame["reason"])
            # The selected BL overwrites LR even if the prologue used x30.
            initial.set("x30", CLEAN)
            initial.set_alias("x30", CLEAN)
            initial.set_control("x30", frozenset({caller + 4}))
            returns = ((caller + 4, initial.sp),)
        initial.set(first.operands[0].value, frozenset({0}))
        initial.set_alias(first.operands[0].value, CLEAN)
        if reg(first.operands[0].value) == "x29":
            initial.fp = None
        enqueue(address + 4, returns, initial)
        while pending:
            if steps >= self.max_steps:
                problem(address, "instruction-budget")
                break
            key = pending.popleft()
            pc, returns, _ = key
            state = states[key].copy()
            steps += 1
            instruction = self.decode(pc)
            live = any(
                tainted(value)
                for value in (*state.registers.values(), *state.stack.values())
            )
            if instruction is None:
                problem(pc, "undecodable-or-outside-executable-bytes")
                continue
            if pc == address or instruction.tpidr_read:
                destination = instruction.operands[0].value
                state.set(destination, frozenset({0}))
                state.set_alias(destination, CLEAN)
                if reg(destination) == "x29":
                    state.fp = None
                enqueue(pc + 4, returns, state)
                continue
            summary = (
                self.decode.atomic32(pc)
                if instruction.mnemonic == "bti" and hasattr(self.decode, "atomic32")
                else None
            )
            if summary is None and instruction.mnemonic == "bti" and hasattr(self.decode, "cas64"):
                summary = self.decode.cas64(pc)
            if summary is not None:
                compare = getattr(summary, "memory_compare", None)
                operation = "atomic-cas64" if compare else "atomic-add32"
                callee_effects[pc] = {
                    "entry": hex(pc),
                    "kind": "exact-" + operation,
                    "body_sha256": summary.body_sha256,
                    "flag_address": hex(summary.flag_address),
                    "rmw_width": summary.rmw_width,
                    "paths": [
                        {
                            "name": path.name,
                            "ret_pc": hex(path.ret_pc),
                            "clobbers": list(path.clobbers),
                            "may_write_memory": path.may_write_memory,
                            "preserves_nzcv": path.preserves_nzcv,
                        }
                        for path in summary.paths
                    ],
                }
                if compare:
                    callee_effects[pc]["memory_order"] = summary.memory_order
                    if tainted(state.value(compare)):
                        problem(pc, "thread-pointer-atomic-compare")
                    if tainted(state.alias(compare)):
                        problem(pc, "stack-address-atomic-compare")
                base = state.value(summary.memory_base)
                if tainted(base):
                    problem(pc, "non-guard-tls-atomic-access")
                    for offset in base:
                        if offset is not None:
                            accesses.add((pc, offset, operation, "unsupported"))
                if tainted(state.value(summary.memory_payload)):
                    problem(pc, "thread-pointer-atomic-payload")
                if tainted(state.alias(summary.memory_payload)):
                    problem(pc, "stack-address-atomic-payload")
                aliases = state.alias(summary.memory_base)
                slot = (
                    next(iter(aliases))
                    if len(aliases) == 1 and None not in aliases
                    else None
                )
                if tainted(aliases) and slot is None:
                    problem(pc, "mixed-stack-address-atomic-access")
                image = state.image(summary.memory_base)
                if (
                    not tainted(base)
                    and not tainted(aliases)
                ):
                    observe_owned_image_access(pc, image, summary.rmw_width, True, True)
                if slot is None:
                    if any(tainted(value) for value in state.stack.values()):
                        problem(
                            pc, "unmodelled-load-may-alias-thread-pointer-stack-spill"
                        )
                    for saved in state.stack_returns:
                        state.stack_returns[saved] = CLEAN
                    for saved in state.stack_aliases:
                        state.stack_aliases[saved] = CLEAN
                    state.stack_images.clear()
                else:
                    for saved in set(state.stack) | set(state.stack_aliases):
                        if (
                            slot < saved + 8
                            and saved < slot + summary.rmw_width
                            and tainted(
                                state.stack.get(saved, CLEAN)
                                | state.stack_aliases.get(saved, CLEAN)
                            )
                        ):
                            problem(
                                pc,
                                "partial-overlap-with-thread-pointer-stack-spill",
                            )
                    for saved in state.stack_returns:
                        if slot < saved + 8 and saved < slot + summary.rmw_width:
                            state.stack_returns[saved] = CLEAN
                    for saved in state.stack_aliases:
                        if slot < saved + 8 and saved < slot + summary.rmw_width:
                            state.stack_aliases[saved] = CLEAN
                    for saved in list(state.stack_images):
                        if slot < saved + 8 and saved < slot + summary.rmw_width:
                            del state.stack_images[saved]
                # Keep every explicit path, including CAS initial mismatch
                # retaining x17 and a mismatch after a failed exclusive store
                # clobbering it. Memory may-write effects above conservatively
                # invalidate aliases for all paths, including non-writing CAS.
                for path in summary.paths:
                    post = state.copy()
                    for name in path.clobbers:
                        post.set(name, CLEAN)
                        post.set_alias(name, CLEAN)
                    enqueue(path.ret_pc, returns, post)
                continue
            if instruction.mnemonic in {"adrp", "bti"} and hasattr(self.decode, "plt"):
                binding = self.decode.plt(pc)
                if binding is not None:
                    plt_bindings[pc] = dict(binding)
                    if binding.get("effect") == "nonreturning":
                        callee_effects[pc] = {
                            "entry": hex(pc),
                            "kind": "native-provider-nonreturning",
                            "binding": dict(binding),
                        }
                        continue
                    if binding["target"] is None:
                        problem(pc, "PLT-binding:" + binding["reason"])
                    else:
                        # The exact stub overwrites both scratch registers and
                        # tail-branches. Preserve the original BL return stack.
                        state.set("x16", CLEAN)
                        state.set("x17", CLEAN)
                        state.set_alias("x16", CLEAN)
                        state.set_alias("x17", CLEAN)
                        enqueue(binding["target"], returns, state)
                    continue
            op, operands = instruction.mnemonic, instruction.operands
            values = [
                state.value(item.value) if item.kind == "reg" else CLEAN
                for item in operands
            ]
            known_control = (
                op == "ret"
                or (op in {"b", "bl"} and operands and operands[0].kind == "imm")
                or op in {"blr", "br", "braa", "brab", "blraa", "blrab"}
                or (
                    (op.startswith("b.") or op in {"cbz", "cbnz", "tbz", "tbnz"})
                    and operands
                    and operands[-1].kind == "imm"
                )
            )
            terminal = op in {
                "svc",
                "hvc",
                "smc",
                "eret",
                "eretaa",
                "eretab",
                "drps",
                "brk",
                "hlt",
                "udf",
                "retaa",
                "retab",
                "braaz",
                "brabz",
                "blraaz",
                "blrabz",
            }
            if terminal or (instruction.control_transfer and not known_control):
                problem(pc, "unhandled-control-transfer:" + op)
                continue
            if op in ORDERED_LOADS | ORDERED_STORES and not valid_ordered_scalar_transfer(
                instruction
            ):
                problem(pc, "malformed-ordered-scalar-transfer")
                continue
            next_pc = pc + 4
            if op == "ret":
                return_register = (
                    operands[0].value
                    if operands and operands[0].kind == "reg"
                    else "x30"
                )
                if tainted(state.value(return_register)):
                    problem(pc, "thread-pointer-used-as-return-target")
                    continue
                if tainted(state.alias(return_register)):
                    problem(pc, "stack-address-used-as-return-target")
                    continue
                if return_register != "x30":
                    problem(pc, "unproved-nonstandard-return-register")
                    continue
                if returns:
                    return_pc, expected_sp = returns[-1]
                    if expected_sp is not None and state.sp != expected_sp:
                        problem(pc, "callee-stack-not-restored-at-return")
                        continue
                    if state.control(return_register) != frozenset({return_pc}):
                        problem(pc, "unproved-return-address")
                        continue
                    enqueue(return_pc, returns[:-1], state)
                elif any(tainted(value) for value in state.stack.values()):
                    problem(pc, "thread-pointer-stack-return-needs-caller-context")
                elif any(tainted(state.value(f"x{i}")) for i in range(9)):
                    problem(pc, "pointer-return-escape")
                elif any(tainted(value) for value in state.registers.values()):
                    problem(pc, "live-pointer-return-needs-caller-context")
                continue
            if op in {"bl", "b"} and operands and operands[0].kind == "imm":
                target = operands[0].value
                if op == "bl":
                    state.set_control("x30", frozenset({next_pc}))
                    callees.add((pc, target))
                    state.set("x30", CLEAN)
                    state.set_alias("x30", CLEAN)
                    state.set_control("x30", frozenset({next_pc}))
                    if len(returns) == MAX_CALL_DEPTH:
                        problem(pc, "callee-depth-limit")
                        continue
                    enqueue(target, returns + ((next_pc, state.sp),), state)
                else:
                    enqueue(target, returns, state)
                continue
            if op in {"blr", "br", "braa", "brab", "blraa", "blrab"}:
                problem(pc, "indirect-call-or-branch-with-unproved-summary")
                continue
            if op.startswith("b.") or op in {"cbz", "cbnz", "tbz", "tbnz"}:
                if any(tainted(value) for value in values):
                    problem(pc, "pointer-in-conditional-control")
                if operands and operands[-1].kind == "imm":
                    enqueue(operands[-1].value, returns, state)
                    enqueue(next_pc, returns, state)
                else:
                    problem(pc, "unresolved-conditional-target")
                continue
            if op in {"adr", "adrp"}:
                if (
                    len(operands) != 2
                    or operands[0].kind != "reg"
                    or not full_width_gpr(operands[0].value)
                    or operands[1].kind != "imm"
                    or type(operands[1].value) is not int
                    or not 0 <= operands[1].value < 1 << 64
                    or (op == "adrp" and operands[1].value & 0xFFF)
                ):
                    problem(pc, "unsupported-pc-relative-address")
                    continue
                destination, immediate = operands
                state.set(destination.value, CLEAN)
                state.set_alias(destination.value, CLEAN)
                state.set_image(destination.value, immediate.value)
                if reg(destination.value) == "x29":
                    state.fp = None
                enqueue(next_pc, returns, state)
                continue
            if op == "csel":
                # Flags are not tracked: retain both complete alternatives,
                # rather than erasing a TP/stack/LR fact on one condition.
                if len(operands) != 3 or any(item.kind != "reg" for item in operands):
                    problem(pc, "malformed-conditional-select")
                    continue
                destination, *sources = operands
                width = destination.value[:1]
                allowed = {width + "zr", *(width + str(index) for index in range(31))}
                if width not in {"x", "w"} or any(item.value not in allowed for item in operands):
                    problem(pc, "unsupported-conditional-select-registers")
                    continue
                for source in sources:
                    post = state.copy()
                    value, alias, control = (
                        state.value(source.value), state.alias(source.value), state.control(source.value)
                    )
                    image = state.image(source.value)
                    if width == "w":
                        if tainted(value):
                            problem(pc, "narrowed-thread-pointer")
                        if tainted(alias):
                            problem(pc, "narrowed-stack-address-unmodelled")
                        value = alias = control = CLEAN
                    post.set(destination.value, value)
                    post.set_alias(destination.value, alias)
                    post.set_control(destination.value, control)
                    post.set_image(destination.value, image if width == "x" else None)
                    if reg(destination.value) == "x29":
                        post.fp = next(iter(alias)) if len(alias) == 1 and None not in alias else None
                    enqueue(next_pc, returns, post)
                continue
            if op == "mov" and len(operands) == 2 and operands[0].kind == "reg":
                destination, source = operands
                image = state.image(source.value) if source.kind == "reg" else None
                control = state.control(source.value) if source.kind == "reg" else CLEAN
                if destination.value.startswith("w"):
                    control = CLEAN
                if destination.value == "wsp":
                    problem(pc, "narrowed-stack-pointer-write-unmodelled")
                    continue
                alias = state.alias(source.value) if source.kind == "reg" else CLEAN
                if destination.value.startswith("w") and tainted(alias):
                    problem(pc, "narrowed-stack-address-unmodelled")
                    alias = CLEAN
                state.set_alias(destination.value, alias)
                if destination.value == "sp":
                    if (
                        source.kind == "reg"
                        and reg(source.value) == "x29"
                        and state.fp is not None
                    ):
                        state.sp = state.fp
                    else:
                        problem(pc, "unmodelled-stack-move")
                        continue
                elif (
                    reg(destination.value) == "x29"
                    and source.kind == "reg"
                    and source.value == "sp"
                ):
                    state.fp = state.sp
                    state.set(destination.value, CLEAN)
                else:
                    if reg(destination.value) == "x29":
                        state.fp = None
                    if destination.value.startswith("w") and tainted(values[1]):
                        problem(pc, "narrowed-thread-pointer")
                    state.set(
                        destination.value,
                        values[1] if not destination.value.startswith("w") else CLEAN,
                    )
                state.set_control(destination.value, control)
                state.set_image(destination.value, image)
            elif (
                op in {"add", "sub"}
                and len(operands) == 3
                and operands[2].kind == "imm"
            ):
                destination, source, immediate = operands
                image = state.image(source.value) if source.kind == "reg" else None
                if destination.value == "wsp":
                    problem(pc, "narrowed-stack-pointer-write-unmodelled")
                    continue
                alias = state.alias(source.value) if source.kind == "reg" else CLEAN
                if destination.value.startswith("w") and tainted(alias):
                    problem(pc, "narrowed-stack-address-unmodelled")
                    alias = CLEAN
                if tainted(alias):
                    alias = frozenset(
                        None
                        if offset is None
                        else offset + immediate.value * (1 if op == "add" else -1)
                        for offset in alias
                    )
                    if any(
                        offset is not None and abs(offset) > MAX_BYTES
                        for offset in alias
                    ):
                        problem(pc, "stack-address-offset-limit")
                        continue
                state.set_alias(destination.value, alias)
                delta = immediate.value * (1 if op == "add" else -1)
                if destination.value == "sp" and source.value != "sp":
                    problem(pc, "unmodelled-stack-write")
                    continue
                if destination.value == "sp" and source.value == "sp":
                    state.sp += delta
                elif reg(destination.value) == "x29" and source.value == "sp":
                    state.fp = state.sp + delta
                    state.set(destination.value, CLEAN)
                else:
                    if reg(destination.value) == "x29":
                        state.fp = None
                    value = frozenset(
                        None if offset is None else offset + delta
                        for offset in values[1]
                    )
                    if any(
                        offset is not None and abs(offset) > 4096 for offset in value
                    ):
                        problem(pc, "pointer-offset-limit")
                        continue
                    state.set(destination.value, value)
                if full_width_gpr(destination.value) and image is not None:
                    state.set_image(destination.value, image + delta)
            elif any(item.kind == "mem" for item in operands):
                memory = next(item for item in operands if item.kind == "mem")
                base_value = state.value(memory.base)
                index_value = state.value(memory.index) if memory.index else CLEAN
                load = op.startswith(("ldr", "ldur", "ldp")) or op in ORDERED_LOADS
                store = op.startswith(("str", "stur", "stp")) or op in ORDERED_STORES
                data = [item for item in operands if item.kind == "reg"]
                if load and len({reg(item.value) for item in data}) != len(data):
                    problem(pc, "unpredictable-duplicate-load-destination")
                    continue
                if instruction.writeback and any(
                    reg(item.value) == reg(memory.base) for item in data
                ):
                    problem(pc, "unpredictable-" + ("load" if load else "store") + "-writeback-overlap")
                    continue
                stack_addresses = state.alias(memory.base)
                stack_base = (
                    next(iter(stack_addresses))
                    if len(stack_addresses) == 1 and None not in stack_addresses
                    else None
                )
                if tainted(stack_addresses) and (stack_base is None or memory.index):
                    problem(pc, "mixed-or-indexed-stack-address-unmodelled")
                slot = (
                    None
                    if stack_base is None or memory.index
                    else stack_base + memory.displacement
                )
                image_base = state.image(memory.base)
                if (
                    (load or store)
                    and bool(data)
                    and not memory.index
                    and not tainted(base_value)
                    and not tainted(stack_addresses)
                    and image_base is not None
                ):
                    observe_owned_image_access(
                        pc, image_base + memory.displacement,
                        instruction.width * len(data), load, store,
                    )
                # CLEAN means "not traced to this MRS", not "cannot alias
                # our stack". A preexisting unknown argument may point at a
                # spilled TP even though exact materialized aliases are tracked.
                # Only singleton known addresses can bypass this uncertainty.
                if (
                    load
                    and slot is None
                    and not tainted(base_value)
                    and not tainted(index_value)
                    and any(tainted(value) for value in state.stack.values())
                ):
                    problem(pc, "unmodelled-load-may-alias-thread-pointer-stack-spill")
                if (
                    store
                    and slot is None
                    and live
                    and any(tainted(state.alias(item.value)) for item in data)
                ):
                    problem(pc, "frame-address-store-escape-unmodelled")
                if tainted(base_value) or tainted(index_value):
                    for offset in base_value:
                        if offset is None:
                            problem(pc, "mixed-pointer-memory-base")
                        elif (
                            memory.index
                            or not load
                            or len(data) != 1
                            or instruction.width != 8
                            or offset + memory.displacement != 40
                        ):
                            problem(pc, "non-guard-or-unmodelled-tls-memory-access")
                            accesses.add(
                                (pc, offset + memory.displacement, op, "unsupported")
                            )
                        else:
                            accesses.add((pc, 40, op, "guard"))
                if not load and not store:
                    problem(pc, "unmodelled-memory-operation")
                if store and slot is None:
                    # Unknown stores can clobber a saved LR even when neither
                    # their address nor payload is derived from this TP.
                    for saved in state.stack_returns:
                        state.stack_returns[saved] = CLEAN
                    for saved in state.stack_aliases:
                        state.stack_aliases[saved] = CLEAN
                    state.stack_images.clear()
                for index, item in enumerate(data):
                    key = None if slot is None else slot + index * instruction.width
                    if store and key is not None:
                        for saved in state.stack_returns:
                            if key < saved + 8 and saved < key + instruction.width:
                                state.stack_returns[saved] = CLEAN
                        for saved in list(state.stack_images):
                            if key < saved + 8 and saved < key + instruction.width:
                                del state.stack_images[saved]
                    if key is not None:
                        for known in set(state.stack) | set(state.stack_aliases):
                            value = state.stack.get(
                                known, CLEAN
                            ) | state.stack_aliases.get(known, CLEAN)
                            if (
                                tainted(value)
                                and key < known + 8
                                and known < key + instruction.width
                                and (key != known or instruction.width != 8)
                            ):
                                problem(
                                    pc,
                                    "partial-overlap-with-thread-pointer-stack-spill",
                                )
                    if load:
                        image = (
                            state.stack_images.get(key)
                            if key is not None and instruction.width == 8
                            else None
                        )
                        state.set(
                            item.value,
                            state.stack.get(key, CLEAN) if key is not None else CLEAN,
                        )
                        alias = (
                            state.stack_aliases.get(key, CLEAN)
                            if key is not None
                            else CLEAN
                        )
                        if instruction.width != 8 and tainted(alias):
                            problem(pc, "narrowed-stack-address-unmodelled")
                            alias = CLEAN
                        state.set_alias(item.value, alias)
                        state.set_control(
                            item.value,
                            state.stack_returns.get(key, CLEAN)
                            if key is not None and instruction.width == 8
                            else CLEAN,
                        )
                        state.set_image(item.value, image)
                        if reg(item.value) == "x29":
                            state.fp = (
                                next(iter(alias))
                                if len(alias) == 1 and None not in alias
                                else None
                            )
                    elif store:
                        value = state.value(item.value)
                        if key is not None and instruction.width == 8:
                            if (
                                len(state.stack) >= MAX_STACK_SLOTS
                                and key not in state.stack
                            ):
                                problem(pc, "stack-slot-limit")
                                continue
                            state.stack[key] = value
                            state.stack_aliases[key] = state.alias(item.value)
                            state.stack_returns[key] = state.control(item.value)
                            image = state.image(item.value)
                            if image is not None:
                                state.stack_images[key] = image
                        elif tainted(value):
                            problem(pc, "thread-pointer-store-escape")
                        elif tainted(state.alias(item.value)):
                            problem(pc, "stack-address-store-unmodelled")
                if instruction.writeback:
                    delta = instruction.post_index or memory.displacement
                    if memory.base == "sp":
                        state.sp += delta
                    else:
                        state.set_alias(
                            memory.base,
                            frozenset(
                                None if value is None else value + delta
                                for value in stack_addresses
                            ),
                        )
                        if reg(memory.base) == "x29":
                            updated = state.aliases.get("x29", CLEAN)
                            state.fp = (
                                next(iter(updated))
                                if len(updated) == 1 and None not in updated
                                else None
                            )
                        state.set(
                            memory.base,
                            frozenset(
                                None if value is None else value + delta
                                for value in base_value
                            ),
                        )
                        state.set_image(
                            memory.base,
                            image_base + delta if image_base is not None else None,
                        )
            else:
                if (
                    live
                    and any(reg(name) in {"sp", "x29"} for name in instruction.reads)
                    and any(
                        reg(name).startswith("x")
                        and reg(name) not in {"x29", "x30", "xzr"}
                        for name in instruction.writes
                    )
                ):
                    problem(pc, "stack-address-materialization-unmodelled")
                if any(tainted(state.alias(name)) for name in instruction.reads):
                    problem(pc, "unmodelled-stack-address-consumer:" + op)
                if any(tainted(state.value(name)) for name in instruction.reads):
                    problem(pc, "unmodelled-thread-pointer-consumer:" + op)
                for name in instruction.writes:
                    state.set(name, CLEAN)
                    state.set_alias(name, CLEAN)
                    if reg(name) == "x29":
                        state.fp = None
                if "sp" in instruction.writes:
                    problem(pc, "unmodelled-stack-write")
                    continue
            # Traverse clean partitions too: they may later meet a loop or a
            # different path, but must never erase a live partition's behavior.
            enqueue(next_pc, returns, state)
        classification = "guard-only" if accesses and not reasons else "unknown"
        return {
            "site": hex(address),
            "caller_context": None if caller is None else hex(caller),
            "caller_frame": caller_frame,
            "classification": classification,
            "steps": steps,
            "analysis_strategy": "bounded-exact-state-partitions",
            "state_partitions": len(states),
            "accesses": [
                {"pc": hex(pc), "offset": offset, "instruction": op, "kind": kind}
                for pc, offset, op, kind in sorted(accesses)
            ],
            "callees": [
                {
                    "call": hex(pc),
                    "target": hex(target),
                    "summary": "explicit-body-traversal-requested",
                }
                for pc, target in sorted(callees)
            ],
            "callee_effects": [callee_effects[key] for key in sorted(callee_effects)],
            "owned_image_accesses": [
                {"pc": hex(pc), "address": hex(start), "width": width,
                 "read": load, "write": store,
                 "owner": "independently-bounded-readable-nonexec-PT_LOAD",
                 "stack_disjointness": "unproved-initial-SP"}
                for pc, start, width, load, store in sorted(owned_image_accesses)
            ],
            "plt_bindings": [
                {"stub": hex(pc), **binding}
                for pc, binding in sorted(plt_bindings.items())
            ],
            "reasons": [
                {"pc": hex(pc), "reason": reason} for pc, reason in sorted(reasons)
            ],
        }


class MetadataUnavailable(ValueError):
    """Valid-looking metadata outside this diagnostic's supported subset."""


class ElfBindingMetadata:
    """Independent bounded cross-check of loader binding evidence against bytes.

    A sectionless ELF or imported named version remains unsupported here. Neither
    symbol visibility nor an unversioned name is used to guess namespace binding.
    """

    def __init__(self, data, fallback_name, provider_bridge=None):
        self.data = data
        self.loads = []
        self.relocations = {}
        self.proof = {}
        self.proof_hash = None
        self.provider_bridge = None
        self.trusted_provider_contracts = False
        self.version_requirements = None
        if provider_bridge is not None:
            provider_bridge = Path(provider_bridge).resolve(strict=True)
            if (
                not provider_bridge.is_file()
                or not 0 < provider_bridge.stat().st_size <= 256 << 20
            ):
                raise ValueError("provider bridge identity size bound")
            self.provider_bridge = {
                "path": str(provider_bridge),
                "sha256": hashlib.sha256(provider_bridge.read_bytes()).hexdigest(),
            }
        self.eh_header = None
        self.eh_ranges = None
        if (
            len(data) < 64
            or len(data) > MAX_BYTES
            or data[:6] != b"\x7fELF\x02\x01"
            or struct.unpack_from("<H", data, 18)[0] != 183
        ):
            raise ValueError("binding metadata requires bounded ELF64 AArch64")
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phcount = struct.unpack_from("<HH", data, 54)
        if (
            phsize != 56
            or not 0 < phcount <= 128
            or phoff + phsize * phcount > len(data)
        ):
            raise ValueError("binding program-header bounds")
        dynamic = None
        for index in range(phcount):
            kind, flags, offset, address, _, size, memory, _ = struct.unpack_from(
                "<IIQQQQQQ", data, phoff + index * phsize
            )
            if kind not in {1, 2, 0x6474E550}:
                continue
            if (
                size > memory
                or offset + size > len(data)
                or address + memory >= 1 << 64
            ):
                raise ValueError("binding segment bounds")
            if kind == 1:
                if any(
                    address < end and start < address + memory
                    for start, end, _, _, _ in self.loads
                ):
                    raise ValueError("overlapping binding LOAD segments")
                self.loads.append((address, address + memory, offset, size, flags))
            elif kind == 0x6474E550:
                if self.eh_header is not None:
                    raise ValueError("duplicate PT_GNU_EH_FRAME")
                self.eh_header = (address, size, offset)
            elif dynamic is not None:
                raise ValueError("duplicate PT_DYNAMIC")
            else:
                dynamic = (address, size)
        if dynamic is None:
            raise MetadataUnavailable("binding metadata lacks PT_DYNAMIC")
        address, size = dynamic
        if size > 1 << 20 or size % 16:
            raise ValueError("dynamic-table bound/alignment")
        self.tags = {}
        self.needed_offsets = []
        terminated = False
        for cursor in range(address, address + size, 16):
            tag, value = struct.unpack("<QQ", self.read(cursor, 16))
            if tag == 0:
                terminated = True
                break
            if tag == 1:
                if len(self.needed_offsets) == 128:
                    raise ValueError("dependency count bound")
                self.needed_offsets.append(value)
                continue
            if tag in {
                2,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                14,
                20,
                23,
                0x6FFFFFF0,
                0x6FFFFFFC,
                0x6FFFFFFD,
                0x6FFFFFFE,
                0x6FFFFFFF,
            }:
                if tag in self.tags:
                    raise ValueError("duplicate relevant dynamic tag")
                self.tags[tag] = value
        if not terminated:
            raise ValueError("unterminated dynamic table")
        if (
            self.tags.get(11) != 24
            or not self.tags.get(5)
            or not self.tags.get(6)
            or not 0 < self.tags.get(10, 0) <= 128 << 20
        ):
            raise MetadataUnavailable("unsupported/missing dynamic symbol metadata")
        self.strings = self.read(self.tags[5], self.tags[10])
        self.needed_libraries = {self.string(offset) for offset in self.needed_offsets}
        shoff = struct.unpack_from("<Q", data, 40)[0]
        shsize, shcount = struct.unpack_from("<HH", data, 58)
        if not shoff or not shcount:
            raise MetadataUnavailable("sectionless dynsym count unsupported")
        if shsize != 64 or shcount > 65535 or shoff + shsize * shcount > len(data):
            raise ValueError("binding section-header bounds")
        sections = [
            struct.unpack_from("<IIQQQQIIQQ", data, shoff + index * shsize)
            for index in range(shcount)
        ]
        candidates = [
            item for item in sections if item[1] == 11 and item[3] == self.tags[6]
        ]
        if len(candidates) != 1:
            raise ValueError("unique matching SHT_DYNSYM required")
        symbol_section = candidates[0]
        _, _, _, symaddr, symoffset, symsize, link, _, _, syment = symbol_section
        if (
            syment != 24
            or symsize % 24
            or not 0 < symsize // 24 <= 2 << 20
            or link >= shcount
        ):
            raise ValueError("dynamic symbol section bounds")
        self.offset(symaddr, symsize)
        if symoffset != self.offset(symaddr, symsize):
            raise ValueError("dynamic symbol section/LOAD mismatch")
        string_section = sections[link]
        if (
            string_section[1] != 3
            or string_section[3] != self.tags[5]
            or string_section[4] != self.offset(self.tags[5], self.tags[10])
            or string_section[5] < self.tags[10]
        ):
            raise ValueError("dynamic string section/LOAD mismatch")
        self.sections = sections
        self.symbol_count = symsize // 24
        self.name = self.string(self.tags[14]) if 14 in self.tags else fallback_name
        if (
            not isinstance(self.name, str)
            or not self.name
            or len(self.name) > 4096
            or "/" in self.name
        ):
            raise ValueError("invalid binding module name")
        if 0x6FFFFFF0 in self.tags:
            if not self.tags[0x6FFFFFF0]:
                raise ValueError("zero VERSYM table")
            self.offset(self.tags[0x6FFFFFF0], self.symbol_count * 2)
        tables = []
        if self.tags.get(8, 0):
            if self.tags.get(9) != 24 or not self.tags.get(7):
                raise ValueError("invalid DT_RELA metadata")
            tables.append((self.tags[7], self.tags[8]))
        if self.tags.get(2, 0):
            if self.tags.get(20) != 7 or not self.tags.get(23):
                raise ValueError("unsupported DT_JMPREL metadata")
            tables.append((self.tags[23], self.tags[2]))
        if sum(length for _, length in tables) > 200000 * 24:
            raise ValueError("binding relocation count bound")
        seen = set()
        for start, length in tables:
            if length % 24:
                raise ValueError("RELA table alignment")
            self.offset(start, length)
            for cursor in range(start, start + length, 24):
                slot, info, addend = struct.unpack("<QQq", self.read(cursor, 24))
                if slot in seen:
                    raise ValueError("duplicate relocation address")
                seen.add(slot)
                kind, index = info & 0xFFFFFFFF, info >> 32
                if kind not in {1025, 1026}:
                    continue
                if index >= self.symbol_count or slot & 7:
                    raise ValueError("PLT/GOT relocation symbol/alignment")
                self.offset(slot, 8)
                self.relocations[slot] = (index, addend, kind)

    def caller_function(self, caller, *, with_source=False):
        """Unique sized symbol entry, or independently checked FDE fallback."""
        matches = set()
        for index in range(self.symbol_count):
            _, info, _, section, value, size = struct.unpack(
                "<IBBHQQ", self.read(self.tags[6] + index * 24, 24)
            )
            if info & 15 != 2 or not 0 < section < 0xFF00 or not size:
                continue
            if not value <= caller or caller + 4 > value + size:
                continue
            if section >= len(self.sections) or value & 3 or size > MAX_BYTES:
                raise ValueError("caller function symbol bounds")
            target_section = self.sections[section]
            if (
                target_section[1] != 1
                or not target_section[2] & 4
                or value < target_section[3]
                or value + size > target_section[3] + target_section[5]
            ):
                raise ValueError("caller function outside executable symbol section")
            self.offset(value, size, executable=True)
            matches.add((value, size))
        source = "ELF-defined-sized-STT_FUNC"
        if not matches:
            matches = {
                (begin, size)
                for begin, size in self.unwind_ranges()
                if begin <= caller and caller + 4 <= begin + size
            }
            source = "ELF-PT_GNU_EH_FRAME-CIE-FDE"
        if len(matches) != 1:
            raise MetadataUnavailable(
                "caller requires unique sized defined function or FDE entry"
            )
        result = next(iter(matches))
        return (*result, source) if with_source else result

    def unwind_ranges(self):
        """Validate the fixed AArch64 EH header plus linked CIE/FDE extents.

        Function sizes come from the FDE itself, never adjacent table entries.
        Unsupported encodings fail explicitly; no CFI execution is inferred.
        """
        if self.eh_ranges is not None:
            return self.eh_ranges
        if self.eh_header is None:
            return ()
        header, header_size, file_offset = self.eh_header
        if not 12 <= header_size <= 16 << 20:
            raise ValueError("unwind header bounds")
        if self.offset(header, header_size) != file_offset:
            raise ValueError("unwind header/LOAD mismatch")
        if self.read(header, 4) != bytes.fromhex("011b033b"):
            raise MetadataUnavailable("unsupported AArch64 unwind header encoding")

        def unsigned(address, width):
            return int.from_bytes(self.read(address, width), "little")

        def relative(base, delta):
            value = base + delta
            if not 0 <= value < 1 << 64:
                raise ValueError("unwind relative address overflow")
            return value

        def signed(address, width):
            return int.from_bytes(self.read(address, width), "little", signed=True)

        def leb(cursor, end, sign=False):
            value = 0
            for index in range(10):
                if cursor >= end:
                    raise ValueError("truncated unwind LEB")
                byte = unsigned(cursor, 1)
                cursor += 1
                payload = byte & 127
                if index == 9 and payload not in ({0, 127} if sign else {0, 1}):
                    raise ValueError("unwind LEB overflow")
                value |= payload << (index * 7)
                if not byte & 128:
                    bits = (index + 1) * 7
                    if sign and byte & 64:
                        value -= 1 << bits
                    return value, cursor
            raise ValueError("unbounded unwind LEB")

        frame = relative(header + 4, signed(header + 4, 4))
        self.read(frame, 4)
        count = unsigned(header + 8, 4)
        if count > 1 << 20 or count > (header_size - 12) // 8:
            raise ValueError("unwind function table bounds")
        cies, ranges = {}, []
        previous = None
        for ordinal in range(count):
            record = header + 12 + ordinal * 8
            begin = relative(header, signed(record, 4))
            fde = relative(header, signed(record + 4, 4))
            if previous is not None and begin < previous:
                raise ValueError("unsorted unwind function table")
            previous = begin
            length, back = unsigned(fde, 4), unsigned(fde + 4, 4)
            if not 12 <= length <= 1 << 20 or not back or back > fde + 4:
                raise ValueError("invalid unwind FDE")
            self.offset(fde, length + 4)
            cie = fde + 4 - back
            if (
                cie < frame
                or fde < frame
                or cie >= fde
                or fde - cie < 4
                or unsigned(cie, 4) > fde - cie - 4
            ):
                raise ValueError("unwind CIE/FDE extent mismatch")
            if cie not in cies:
                cie_length = unsigned(cie, 4)
                if not 8 <= cie_length <= 65536 or unsigned(cie + 4, 4):
                    raise ValueError("invalid unwind CIE")
                self.offset(cie, cie_length + 4)
                cursor, end = cie + 8, cie + 4 + cie_length
                version = unsigned(cursor, 1)
                cursor += 1
                if version not in {1, 3}:
                    raise MetadataUnavailable("unsupported unwind CIE version")
                augmentation = bytearray()
                while True:
                    if cursor >= end or len(augmentation) >= 32:
                        raise ValueError("unbounded unwind augmentation")
                    byte = unsigned(cursor, 1)
                    cursor += 1
                    if not byte:
                        break
                    augmentation.append(byte)
                _, cursor = leb(cursor, end)
                _, cursor = leb(cursor, end, True)
                if version == 1:
                    if cursor >= end:
                        raise ValueError("truncated CIE return register")
                    return_register = unsigned(cursor, 1)
                    cursor += 1
                else:
                    return_register, cursor = leb(cursor, end)
                if return_register != 30:
                    raise MetadataUnavailable("unsupported AArch64 CIE return register")
                encoding = 0
                if augmentation:
                    if augmentation[0] != ord("z"):
                        raise MetadataUnavailable("unsupported unwind augmentation")
                    size, cursor = leb(cursor, end)
                    finish = cursor + size
                    if finish > end:
                        raise ValueError("truncated CIE augmentation")
                    for character in augmentation[1:]:
                        if character in b"BS":
                            continue
                        if cursor >= finish:
                            raise ValueError("missing CIE augmentation field")
                        value = unsigned(cursor, 1)
                        cursor += 1
                        if character == ord("R"):
                            encoding = value
                        elif character == ord("P"):
                            form = value & 15
                            if form in {1, 9}:
                                _, cursor = leb(cursor, finish, form == 9)
                            else:
                                width = {
                                    0: 8,
                                    2: 2,
                                    3: 4,
                                    4: 8,
                                    10: 2,
                                    11: 4,
                                    12: 8,
                                }.get(form)
                                if width is None:
                                    raise MetadataUnavailable(
                                        "unsupported unwind pointer format"
                                    )
                                if cursor + width > finish:
                                    raise ValueError("truncated unwind pointer")
                                self.read(cursor, width)
                                cursor += width
                        elif character != ord("L"):
                            raise MetadataUnavailable("unknown CIE augmentation")
                    if cursor != finish:
                        raise ValueError("extra CIE augmentation data")
                if encoding != 0x1B:
                    raise MetadataUnavailable(
                        "unsupported AArch64 FDE pointer encoding"
                    )
                cies[cie] = encoding
            # Accepted CIEs use z-augmentation. Its FDE payload must itself
            # fit the declared record even though no LSDA/CFI is executed here.
            augmentation_size, augmentation_start = leb(fde + 16, fde + 4 + length)
            if augmentation_start + augmentation_size > fde + 4 + length:
                raise ValueError("truncated FDE augmentation")
            actual_begin = relative(fde + 8, signed(fde + 8, 4))
            size = signed(fde + 12, 4)
            if actual_begin != begin or begin & 3 or size <= 0:
                raise ValueError("unwind FDE/header function mismatch")
            self.offset(begin, size, executable=True)
            ranges.append((begin, size))
        self.eh_ranges = tuple(ranges)
        return self.eh_ranges

    def offset(self, address, size, executable=False):
        if (
            not isinstance(address, int)
            or not isinstance(size, int)
            or address < 0
            or size < 0
            or address + size >= 1 << 64
        ):
            raise ValueError("binding virtual range overflow")
        for start, _, offset, file_size, flags in self.loads:
            if (
                flags & 4
                and (not executable or flags & 1)
                and start <= address
                and address + size <= start + file_size
            ):
                return offset + address - start
        raise ValueError("binding range outside readable file-backed LOAD")

    def read(self, address, size):
        offset = self.offset(address, size)
        return self.data[offset : offset + size]

    def string(self, offset):
        if not isinstance(offset, int) or not 0 <= offset < len(self.strings):
            raise ValueError("dynamic string offset")
        end = self.strings.find(b"\0", offset, min(len(self.strings), offset + 4097))
        if end < 0:
            raise ValueError("unbounded dynamic string")
        try:
            return self.strings[offset:end].decode("utf-8")
        except UnicodeDecodeError as error:
            raise ValueError("non-UTF8 diagnostic symbol name") from error

    @staticmethod
    def number(value):
        if isinstance(value, str) and len(value) <= 18 and value.startswith("0x"):
            value = int(value, 16)
        if type(value) is not int or not 0 <= value < 1 << 64:
            raise ValueError("invalid binding relative address")
        return value

    def load_proof(self, report, report_hash):
        if (
            not isinstance(report, dict)
            or type(report.get("schema")) is not int
            or report["schema"] != 1
        ):
            raise ValueError("unsupported binding evidence schema")
        if (
            report.get("namespace") != "nativebridge"
            or report.get("snapshot") != "post-link-before-open"
        ):
            raise MetadataUnavailable(
                "binding proof is not an actual NativeBridge namespace snapshot"
            )
        modules = report.get("modules")
        if not isinstance(modules, list) or len(modules) > 128:
            raise ValueError("binding evidence module bound")
        names = set()
        own = None
        for module in modules:
            if (
                not isinstance(module, dict)
                or not isinstance(module.get("name"), str)
                or len(module["name"]) > 4096
                or module["name"] in names
            ):
                raise ValueError("duplicate/invalid binding evidence module")
            names.add(module["name"])
            digest = module.get("elf_sha256")
            if (
                not isinstance(digest, str)
                or len(digest) != 64
                or any(character not in "0123456789abcdef" for character in digest)
            ):
                raise ValueError("invalid binding evidence ELF hash")
            if module["name"] == self.name:
                own = module
        self.proof_hash = report_hash
        if self.provider_bridge is not None:
            identity = report.get("native_bridge")
            if (
                not isinstance(identity, dict)
                or identity.get("path") != self.provider_bridge["path"]
                or identity.get("sha256") != self.provider_bridge["sha256"]
                or identity.get("verification") != "observed-before-and-after-process"
            ):
                raise ValueError(
                    "provider bridge identity disagrees with binding report"
                )
            self.trusted_provider_contracts = True
        if own is None:
            return
        if own["elf_sha256"] != hashlib.sha256(self.data).hexdigest():
            raise ValueError("binding evidence ELF hash mismatch")
        bindings = own.get("bindings")
        if not isinstance(bindings, list) or len(bindings) > 200000:
            raise ValueError("binding evidence relocation bound")
        for binding in bindings:
            if not isinstance(binding, dict):
                raise TypeError("invalid binding evidence record")
            slot = self.number(binding.get("relocation"))
            if slot in self.proof:
                raise ValueError("duplicate binding evidence relocation")
            for name in ("symbol", "version", "provider", "kind", "abi"):
                if not isinstance(binding.get(name), str) or len(binding[name]) > 4096:
                    raise ValueError("invalid binding evidence field")
            if binding.get("target") is not None:
                self.number(binding["target"])
            provider_hash = binding.get("provider_elf_sha256")
            if provider_hash is not None and (
                not isinstance(provider_hash, str)
                or len(provider_hash) != 64
                or any(
                    character not in "0123456789abcdef" for character in provider_hash
                )
            ):
                raise ValueError("invalid binding provider ELF hash")
            if type(binding.get("nonreturning", False)) is not bool:
                raise ValueError("invalid nonreturning contract field")
            self.proof[slot] = dict(binding)

    def needed_version(self, index):
        start, count = self.tags.get(0x6FFFFFFE, 0), self.tags.get(0x6FFFFFFF, 0)
        if not start and not count:
            return None
        if not start or not 0 < count <= 4096:
            raise ValueError("version requirement count/address bound")
        if self.version_requirements is None:
            requirements, cursor, total = {}, start, 0
            for ordinal in range(count):
                revision, auxiliaries, filename, auxiliary, following = struct.unpack(
                    "<HHIII", self.read(cursor, 16)
                )
                library = self.string(filename)
                if (
                    revision != 1
                    or not 0 < auxiliaries <= 4096
                    or auxiliary < 16
                    or not library
                    or library not in self.needed_libraries
                ):
                    raise ValueError("invalid version requirement header/dependency")
                total += auxiliaries
                if total > 8192:
                    raise ValueError("version requirement auxiliary total bound")
                aux = cursor + auxiliary
                for number in range(auxiliaries):
                    expected_hash, _, other, name_offset, next_aux = struct.unpack(
                        "<IHHII", self.read(aux, 16)
                    )
                    version = self.string(name_offset)
                    hashed = 0
                    for byte in version.encode():
                        hashed = ((hashed << 4) + byte) & 0xFFFFFFFF
                        top = hashed & 0xF0000000
                        if top:
                            hashed ^= top >> 24
                        hashed &= ~top
                    other &= 0x7FFF
                    if (
                        not version
                        or other <= 1
                        or other in requirements
                        or hashed != expected_hash
                    ):
                        raise ValueError("invalid version requirement name/hash/index")
                    requirements[other] = (version, library)
                    if number + 1 < auxiliaries:
                        if next_aux < 16:
                            raise ValueError(
                                "truncated version requirement auxiliary chain"
                            )
                        aux += next_aux
                    elif next_aux:
                        raise ValueError(
                            "unterminated version requirement auxiliary chain"
                        )
                if ordinal + 1 < count:
                    if following < 16:
                        raise ValueError("truncated version requirement chain")
                    cursor += following
                elif following:
                    raise ValueError("unterminated version requirement chain")
            self.version_requirements = requirements
        return self.version_requirements.get(index)

    def defined_version(self, index):
        """Read a named definition independently; never infer it from the proof."""
        start = self.tags.get(0x6FFFFFFC, 0)
        count = self.tags.get(0x6FFFFFFD, 0)
        if not start or not count:
            return None
        if not 0 < count <= 4096:
            raise ValueError("version definition count bound")
        cursor, seen, definitions = start, set(), {}
        auxiliary_total = 0
        for ordinal in range(count):
            if cursor in seen:
                raise ValueError("cyclic version definition")
            seen.add(cursor)
            revision, flags, number, auxiliaries, _, auxiliary, following = (
                struct.unpack("<HHHHIII", self.read(cursor, 20))
            )
            if revision != 1 or not auxiliaries or auxiliaries > 4096 or auxiliary < 20:
                raise ValueError("invalid version definition header")
            if number == 0 or number & 0x8000 or number in definitions:
                raise ValueError("duplicate/invalid version definition index")
            auxiliary_total += auxiliaries
            if auxiliary_total > 8192:
                raise ValueError("version auxiliary total bound")
            aux_cursor, aux_seen, name = cursor + auxiliary, set(), None
            for aux_index in range(auxiliaries):
                if aux_cursor in aux_seen:
                    raise ValueError("cyclic version auxiliary")
                aux_seen.add(aux_cursor)
                name_offset, next_aux = struct.unpack("<II", self.read(aux_cursor, 8))
                text = self.string(name_offset)
                if not text:
                    raise ValueError("empty version definition name")
                if aux_index == 0:
                    name = text
                if aux_index + 1 < auxiliaries:
                    if next_aux < 8:
                        raise ValueError("truncated version auxiliary chain")
                    aux_cursor += next_aux
                elif next_aux:
                    raise ValueError("unterminated version auxiliary chain")
            # VER_FLG_BASE is the object's own identity, not a named export version.
            definitions[number] = None if flags & 1 else name
            if ordinal + 1 < count:
                if following < 20:
                    raise ValueError("truncated version definition chain")
                cursor += following
            elif following:
                raise ValueError("unterminated version definition chain")
        return definitions.get(index)

    def resolve(self, slot):
        if slot not in self.relocations:
            return {
                "target": None,
                "reason": "GOT-slot-lacks-supported-relocation",
                "slot": hex(slot),
            }
        index, addend, kind = self.relocations[slot]
        name, info, _, section, value, _ = struct.unpack(
            "<IBBHQQ", self.read(self.tags[6] + index * 24, 24)
        )
        symbol = self.string(name)
        result = {
            "target": None,
            "slot": hex(slot),
            "symbol": symbol,
            "relocation_type": kind,
        }
        requested_version = ""
        required_library = None
        if 0x6FFFFFF0 in self.tags:
            version = (
                struct.unpack("<H", self.read(self.tags[0x6FFFFFF0] + index * 2, 2))[0]
                & 0x7FFF
            )
            if version > 1:
                if not 0 < section < 0xFF00:
                    requirement = self.needed_version(version)
                    if requirement is None:
                        return {
                            **result,
                            "reason": "imported-symbol-version-unsupported",
                        }
                    requested_version, required_library = requirement
                else:
                    requested_version = self.defined_version(version)
                if requested_version is None:
                    return {**result, "reason": "named-symbol-version-unsupported"}
        proof = self.proof.get(slot)
        if proof is None:
            return {**result, "reason": "namespace-binding-proof-missing"}
        if proof["symbol"] != symbol or proof["version"] != requested_version:
            raise ValueError("binding evidence symbol/version mismatch")
        if proof.get("nonreturning", False):
            if not self.trusted_provider_contracts:
                return {**result, "reason": "native-provider-contract-untrusted"}
            if (
                addend != 0
                or proof.get("addend") != 0
                or proof.get("relocation_type") != kind
                or proof["kind"] != "function"
                or info & 15 != 2
                or proof["abi"] != "fixed/nonreturning-noargs"
                or proof.get("target") is not None
                or proof.get("provider_elf_sha256") is not None
                or not proof["provider"]
                or (
                    required_library is not None
                    and proof["provider"] != required_library
                )
            ):
                return {**result, "reason": "unsupported-native-provider-contract"}
            return {
                **result,
                "effect": "nonreturning",
                "provider": proof["provider"],
                "bridge_sha256": self.provider_bridge["sha256"],
                "reason": "verified-explicit-nonreturning-noargs-provider",
            }
        if (
            proof["provider"] != self.name
            or proof.get("provider_elf_sha256") != hashlib.sha256(self.data).hexdigest()
        ):
            return {**result, "reason": "external-or-preempted-namespace-binding"}
        if (
            proof["kind"] != "function"
            or proof["abi"] not in {"guest-AAPCS64", "guest-local-AAPCS64"}
            or info & 15 != 2
            or not 0 < section < 0xFF00
        ):
            return {**result, "reason": "nonlocal-or-unsupported-function-binding"}
        target = value + addend
        if (
            not 0 <= target < 1 << 64
            or proof.get("target") is None
            or self.number(proof["target"]) != target
        ):
            raise ValueError("binding evidence target mismatch")
        if section >= len(self.sections):
            raise ValueError("function symbol section index outside table")
        symbol_section = self.sections[section]
        if (
            symbol_section[1] != 1
            or not symbol_section[2] & 4
            or not symbol_section[3] <= target
            or target + 4 > symbol_section[3] + symbol_section[5]
        ):
            return {
                **result,
                "reason": "function-target-outside-executable-symbol-section",
            }
        if target & 3:
            raise ValueError("unaligned binding function target")
        self.offset(target, 4, executable=True)
        return {
            **result,
            "target": target,
            "provider": self.name,
            "elf_sha256": proof["provider_elf_sha256"],
            "reason": "same-module-production-binding",
        }


def plt_stub(decode, address, bindings):
    """Recognize only the standard AArch64 x16/x17 GOT stub, not arbitrary BR."""
    cursor = address
    first = decode(cursor)
    if first is not None and first.mnemonic == "bti":
        cursor += 4
    sequence = [decode(cursor + index * 4) for index in range(4)]
    if any(item is None for item in sequence):
        return None
    page, load, add, branch = sequence
    if (
        page.mnemonic != "adrp"
        or len(page.operands) != 2
        or page.operands[0] != Operand("reg", "x16")
        or page.operands[1].kind != "imm"
    ):
        return None
    if (
        load.mnemonic != "ldr"
        or len(load.operands) != 2
        or load.operands[0] != Operand("reg", "x17")
        or load.operands[1].kind != "mem"
        or load.operands[1].base != "x16"
        or load.operands[1].index
        or load.writeback
        or load.width != 8
    ):
        return None
    displacement = load.operands[1].displacement
    if (
        add.mnemonic != "add"
        or add.operands
        != (Operand("reg", "x16"), Operand("reg", "x16"), Operand("imm", displacement))
        or branch.mnemonic != "br"
        or branch.operands != (Operand("reg", "x17"),)
        or not 0 <= displacement <= 4095
    ):
        return None
    if (
        type(page.operands[1].value) is not int
        or page.operands[1].value & 4095
        or not 0 <= page.operands[1].value <= (1 << 64) - 4096
    ):
        return None
    slot = page.operands[1].value + displacement
    if bindings is None:
        return {
            "target": None,
            "slot": hex(slot),
            "reason": "namespace-binding-metadata-missing",
        }
    return bindings.resolve(slot)


class ElfDecoder:
    def __init__(self, path, binding_report=None, provider_bridge=None):
        import capstone
        from capstone import arm64

        if capstone.__version__ != "5.0.3":
            raise ValueError("audit requires pinned Capstone 5.0.3")
        self.arm64 = arm64
        if not 64 <= path.stat().st_size <= MAX_BYTES:
            raise ValueError("ELF size bound")
        with path.open("rb") as stream:
            self.data = stream.read(MAX_BYTES + 1)
        library = Path(capstone._cs._name)
        self.identity = {
            "version": capstone.__version__,
            "callee_effects_sha256": hashlib.sha256(
                Path(guest_callee_effects.__file__).read_bytes()
            ).hexdigest(),
            "bindings_sha256": hashlib.sha256(
                Path(capstone.__file__).read_bytes()
            ).hexdigest(),
            "native_library_sha256": hashlib.sha256(library.read_bytes()).hexdigest(),
        }
        if len(self.data) > MAX_BYTES or len(self.data) < 64:
            raise ValueError("ELF size bound")
        if (
            self.data[:6] != b"\x7fELF\x02\x01"
            or struct.unpack_from("<H", self.data, 18)[0] != 183
        ):
            raise ValueError("requires little-endian ELF64 AArch64")
        offset = struct.unpack_from("<Q", self.data, 32)[0]
        entry_size, count = struct.unpack_from("<HH", self.data, 54)
        if (
            entry_size != 56
            or not 0 < count <= 128
            or offset + count * entry_size > len(self.data)
        ):
            raise ValueError("program-header bounds")
        self.segments = []
        for index in range(count):
            kind, flags, file_offset, address, _, size, memory_size, _ = (
                struct.unpack_from("<IIQQQQQQ", self.data, offset + index * entry_size)
            )
            if kind != 1 or not flags & 1:
                continue
            if (
                size > memory_size
                or file_offset + size > len(self.data)
                or address + size >= 1 << 64
            ):
                raise ValueError("executable LOAD bounds")
            if any(
                address < end and start < address + size
                for start, end, _ in self.segments
            ):
                raise ValueError("overlapping executable LOAD segments")
            self.segments.append((address, address + size, file_offset))
            if sum(end - start for start, end, _ in self.segments) > MAX_BYTES:
                raise ValueError("aggregate executable-byte bound")
        self.decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
        self.decoder.detail = True
        self.control_groups = {
            capstone.CS_GRP_JUMP,
            capstone.CS_GRP_CALL,
            capstone.CS_GRP_RET,
            capstone.CS_GRP_INT,
            capstone.CS_GRP_IRET,
            capstone.CS_GRP_BRANCH_RELATIVE,
        }
        self.cache = {}
        self.plt_cache = {}
        self.bindings = None
        self.binding_metadata_unavailable = None
        if binding_report is not None:
            if not 0 < binding_report.stat().st_size <= 64 << 20:
                raise ValueError("binding evidence file bound")
            with binding_report.open("rb") as stream:
                proof_bytes = stream.read((64 << 20) + 1)
            if len(proof_bytes) > 64 << 20:
                raise ValueError("binding evidence file bound")
            proof = json.loads(proof_bytes)
            try:
                self.bindings = ElfBindingMetadata(
                    self.data, path.name, provider_bridge
                )
                self.bindings.load_proof(proof, hashlib.sha256(proof_bytes).hexdigest())
            except MetadataUnavailable as error:
                self.binding_metadata_unavailable = str(error)

    def atomic32(self, address):
        return self.atomic_effect(address, guest_callee_effects.recognize_atomic32)

    def cas64(self, address):
        return self.atomic_effect(address, guest_callee_effects.recognize_cas64)

    def atomic_effect(self, address, recognize):
        if self.bindings is None:
            return None

        def word(pc):
            raw = self.word(pc)
            return int.from_bytes(raw, "little") if raw is not None else None

        def allocated_data(start, width):
            return any(
                flags & 4 and not flags & 1 and begin <= start and start + width <= end
                for begin, end, _, _, flags in self.bindings.loads
            )

        return recognize(word, address, allocated_data)

    def owned_image_data(self, address, width):
        if (
            self.bindings is None
            or type(address) is not int
            or type(width) is not int
            or width <= 0
            or not 0 <= address < 1 << 64
            or address + width > 1 << 64
        ):
            return False
        return any(
            flags & 4 and not flags & 1 and start <= address and address + width <= end
            for start, end, _, _, flags in self.bindings.loads
        )

    def caller_frame(self, caller):
        try:
            metadata = self.bindings or ElfBindingMetadata(
                self.data, "<caller-context>"
            )
            root, size, source = metadata.caller_function(caller, with_source=True)
            state = caller_prologue(self, root, caller)
        except MetadataUnavailable as error:
            return None, {"status": "unknown", "reason": str(error)}
        return state, {
            "status": "validated-entry-context",
            "source": source,
            "entry": hex(root),
            "size": size,
            "instructions": (caller - root) // 4,
            "sp": state.sp,
            "fp": state.fp,
            "scope": "straight-line path from this metadata entry to selected BL only; alternate entries and complete helper references unproved",
        }

    def plt(self, address):
        if address not in self.plt_cache:
            result = plt_stub(self, address, self.bindings)
            if result is not None and self.binding_metadata_unavailable:
                result = {**result, "reason": self.binding_metadata_unavailable}
            if len(self.plt_cache) < 1 << 20:
                self.plt_cache[address] = result
            return result
        return self.plt_cache[address]

    def word(self, address):
        if address & 3:
            return None
        for start, end, offset in self.segments:
            if start <= address and address + 4 <= end:
                return self.data[
                    offset + address - start : offset + address - start + 4
                ]
        return None

    def sites(self):
        result = []
        for start, end, _ in self.segments:
            for address in range((start + 3) & ~3, end - 3, 4):
                if (
                    int.from_bytes(self.word(address), "little") & 0xFFFFFFE0
                    == 0xD53BD040
                ):
                    result.append(address)
                    if len(result) > MAX_SITES:
                        raise ValueError("TPIDR candidate bound")
        return result

    def __call__(self, address):
        if address in self.cache:
            return self.cache[address]
        raw = self.word(address)
        if raw is None:
            return None
        decoded = next(self.decoder.disasm(raw, address, count=1), None)
        if decoded is None or decoded.size != 4:
            return None
        operands = []
        for item in decoded.operands:
            if item.type == self.arm64.ARM64_OP_REG:
                operands.append(Operand("reg", decoded.reg_name(item.reg)))
            elif item.type == self.arm64.ARM64_OP_IMM:
                if item.shift.value:
                    operands.append(Operand("other"))
                else:
                    operands.append(Operand("imm", item.imm))
            elif item.type == self.arm64.ARM64_OP_MEM:
                operands.append(
                    Operand(
                        "mem",
                        base=decoded.reg_name(item.mem.base),
                        index=decoded.reg_name(item.mem.index)
                        if item.mem.index
                        else "",
                        displacement=item.mem.disp,
                    )
                )
            else:
                operands.append(Operand("other"))
        reads, writes = decoded.regs_access()
        width = memory_width(
            decoded.mnemonic,
            operands[0].value if operands and operands[0].kind == "reg" else "",
        )
        post_index = (
            operands[-1].value
            if decoded.writeback and operands and operands[-1].kind == "imm"
            else 0
        )
        result = Instruction(
            address,
            decoded.mnemonic,
            tuple(operands),
            tuple(decoded.reg_name(value) for value in reads),
            tuple(decoded.reg_name(value) for value in writes),
            decoded.writeback,
            post_index,
            width,
            decoded.op_str,
            int.from_bytes(raw, "little") & 0xFFFFFFE0 == 0xD53BD040,
            bool(set(decoded.groups) & self.control_groups),
        )
        if len(self.cache) < 1 << 20:
            self.cache[address] = result
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--site", action="append", type=lambda value: int(value, 0))
    parser.add_argument(
        "--bindings",
        type=Path,
        help="hash-matched production NativeBridge namespace binding sidecar",
    )
    parser.add_argument(
        "--caller",
        type=lambda value: int(value, 0),
        help="one verified direct BL context for a single --site MRS-return helper",
    )
    parser.add_argument(
        "--provider-bridge",
        type=Path,
        help="explicit trusted first-party bridge image matching provider contracts in the binding report",
    )
    args = parser.parse_args()
    if args.caller is not None and (not args.site or len(args.site) != 1):
        parser.error("--caller requires exactly one --site")
    if args.provider_bridge is not None and args.bindings is None:
        parser.error("--provider-bridge requires an actual binding report")
    decoder = ElfDecoder(args.elf, args.bindings, args.provider_bridge)
    candidates = args.site if args.site else decoder.sites()
    if len(candidates) > MAX_SITES:
        parser.error("too many requested candidate sites")
    results = []
    total_steps = 0
    for site in candidates:
        raw = decoder.word(site)
        if raw is None or int.from_bytes(raw, "little") & 0xFFFFFFE0 != 0xD53BD040:
            parser.error(f"not a file-backed MRS TPIDR_EL0 candidate: {site:#x}")
        if total_steps >= MAX_TOTAL_STEPS:
            result = {
                "site": hex(site),
                "classification": "unknown",
                "steps": 0,
                "reasons": [{"pc": hex(site), "reason": "total-instruction-budget"}],
            }
        else:
            result = Audit(
                decoder, max_steps=min(MAX_STEPS, MAX_TOTAL_STEPS - total_steps)
            ).site(site, caller=args.caller)
        total_steps += result.get("steps", 0)
        results.append(result)
    report = {
        "schema": 1,
        "analyzer_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "decoder": "capstone-5.0.3",
        "decoder_identity": decoder.identity,
        "provider_bridge_identity": decoder.bindings.provider_bridge
        if decoder.bindings
        else None,
        "binding_proof_sha256": decoder.bindings.proof_hash
        if decoder.bindings
        else None,
        "binding_metadata_unavailable": decoder.binding_metadata_unavailable,
        "elf_sha256": hashlib.sha256(decoder.data).hexdigest(),
        "scope": "candidate-consumer diagnostic; independent loader code provenance required; no ELF TLS claim",
        "limits": {
            "steps_per_site": MAX_STEPS,
            "contexts_per_site": MAX_CONTEXTS,
            "call_depth": MAX_CALL_DEPTH,
            "stack_slots": MAX_STACK_SLOTS,
            "values_per_join": MAX_VALUES,
            "total_steps": MAX_TOTAL_STEPS,
            "caller_prelude_instructions": MAX_CALLER_PRELUDE,
        },
        "sites": results,
        "counts": {
            kind: sum(item["classification"] == kind for item in results)
            for kind in ("guard-only", "unknown")
        },
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["counts"]))


if __name__ == "__main__":
    main()
