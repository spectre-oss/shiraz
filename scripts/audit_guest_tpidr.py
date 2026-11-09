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
from bisect import bisect_left
from collections import deque
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path

import guest_allocator_contract
import guest_callee_effects
import guest_frame_effects
import guest_jump_table
import guest_memory_contract
import guest_mrs_window
import guest_native_target

MAX_BYTES = 256 << 20
MAX_SITES = 131072
MAX_STEPS = 8192
MAX_CONTEXTS = 4096
MAX_CALL_DEPTH = 4
MAX_STACK_SLOTS = 256
MAX_VALUES = 8
MAX_TOTAL_STEPS = 2_000_000
MAX_CALLER_PRELUDE = 64
MAX_BYTE_COPY = 4096
MAX_INDIRECT_SYMBOL_WORK = 2_000_000
MAX_INDIRECT_ENTRIES = 4096
CLEAN = frozenset({None})


class DerivedBits(Enum):
    OPAQUE = "opaque-thread-pointer-derived-bits"


OPAQUE = DerivedBits.OPAQUE
OPAQUE_VALUE = frozenset({OPAQUE})
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
    if destination.startswith("b"):
        return 1
    if destination.startswith("h"):
        return 2
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
    shift: str = ""
    shift_amount: int = 0
    extend: str = ""


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


@dataclass(frozen=True)
class ReturnSignature:
    tokens: frozenset
    sp: int
    key: str


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
    # Symbolic identities, not PAC bits or a cryptographic validation claim.
    return_signatures: dict = field(default_factory=dict)
    stack_signatures: dict = field(default_factory=dict)
    # A native callee can save input registers in its frames below incoming SP.
    # Exact modeled 8-byte guest stores can replace individual spill slots;
    # this does not bound the other native spill bytes or clear latent risk.
    native_stack_clobber_below: int | None = None
    native_memory_may_contain_tp: bool = False
    native_stack_rewritten: set = field(default_factory=set)
    # Exact integer bits only; CLEAN alone never supplies a scalar value.
    constants: dict = field(default_factory=dict)
    # Architectural W-register writes zero the upper half of their X alias.
    # CLEAN alone says nothing about these bits. X writes invalidate the fact.
    zero_upper: set = field(default_factory=set)

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
            dict(self.return_signatures),
            dict(self.stack_signatures),
            self.native_stack_clobber_below,
            self.native_memory_may_contain_tp,
            set(self.native_stack_rewritten),
            dict(self.constants),
            set(self.zero_upper),
        )

    def fingerprint(self):
        # Missing register facts and explicit CLEAN facts have identical reads,
        # liveness and limits. Canonicalize those defaults, without dropping
        # stack entries: even CLEAN spill slots affect bounded slot accounting.
        return (
            self.sp,
            self.fp,
            self.native_stack_clobber_below,
            self.native_memory_may_contain_tp,
            tuple(sorted(self.native_stack_rewritten)),
            tuple(sorted(self.constants.items())),
            tuple(sorted(self.zero_upper)),
            *(
                tuple(sorted((key, value) for key, value in mapping.items()
                             if not defaults or value != CLEAN))
                for mapping, defaults in (
                    (self.registers, True),
                    (self.stack, False),
                    (self.aliases, True),
                    (self.stack_aliases, False),
                    (self.return_values, True),
                    (self.stack_returns, False),
                    (self.image_addresses, False),
                    (self.stack_images, False),
                    (self.return_signatures, False),
                    (self.stack_signatures, False),
                )
            ),
        )

    def control(self, name):
        return self.return_values.get(reg(name), CLEAN)

    def set_control(self, name, value):
        if full_width_gpr(name):
            self.zero_upper.discard(reg(name))
        name = reg(name)
        if name not in {"xzr", "wzr", "sp"}:
            self.constants.pop(name, None)
            self.return_values[name] = value
            self.return_signatures.pop(name, None)

    def signature(self, name):
        return self.return_signatures.get(reg(name)) if full_width_gpr(name) else None

    def set_signature(self, name, signature):
        normalized = reg(name)
        if full_width_gpr(name):
            self.zero_upper.discard(normalized)
        self.constants.pop(normalized, None)
        self.return_signatures.pop(normalized, None)
        if full_width_gpr(name) and isinstance(signature, ReturnSignature):
            self.return_signatures[normalized] = signature
            self.return_values[normalized] = CLEAN

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
        normalized = reg(name)
        if normalized in {f"x{i}" for i in range(31)}:
            if name.startswith("w"):
                self.zero_upper.add(normalized)
            else:
                self.zero_upper.discard(normalized)
        name = reg(name)
        if name not in {"xzr", "wzr", "sp"}:
            self.constants.pop(name, None)
            self.registers[name] = value
            self.return_values[name] = CLEAN
            self.image_addresses.pop(name, None)
            self.return_signatures.pop(name, None)

    def scalar(self, name):
        if name in {"xzr", "wzr"}:
            return 0
        value = self.constants.get(reg(name))
        return value & 0xFFFFFFFF if value is not None and name.startswith("w") else value

    def set_scalar(self, name, value):
        if value is not None and (full_width_gpr(name) or name.startswith("w") and
                                  reg(name) in {f"x{i}" for i in range(31)}):
            bits = value & ((1 << (32 if name.startswith("w") else 64)) - 1)
            self.constants[reg(name)] = bits
            if bits <= 0xFFFFFFFF:
                self.zero_upper.add(reg(name))
            else:
                self.zero_upper.discard(reg(name))

    def image(self, name):
        return self.image_addresses.get(reg(name)) if full_width_gpr(name) else None

    def set_image(self, name, value):
        normalized = reg(name)
        self.image_addresses.pop(normalized, None)
        if full_width_gpr(name) and type(value) is int and 0 <= value < 1 << 64:
            self.image_addresses[normalized] = value


def tainted(value):
    return any(item is not None for item in value)


def exact_register_delta(operands, state):
    """A64 64-bit ADD/SUB index bits; no unknown integer becomes an address fact."""
    destination, source, index = operands
    if (any(item.kind != "reg" for item in operands)
            or not full_width_gpr(destination.value)
            or not (full_width_gpr(source.value) or source.value in {"sp", "xzr"})
            or any(item.shift or item.shift_amount or item.extend for item in (destination, source))
            or tainted(state.value(index.value)) or tainted(state.alias(index.value))
            or tainted(state.control(index.value)) or state.signature(index.value) is not None
            or state.image(index.value) is not None):
        return None
    value = state.scalar(index.value)
    if value is None or type(index.shift_amount) is not int:
        return None
    if index.extend in {"uxtw", "sxtw"}:
        if (source.value == "xzr" or not index.value.startswith("w") or
                reg(index.value) not in {f"x{i}" for i in range(31)} | {"xzr"}
                or index.shift not in {"", "lsl"} or not 0 <= index.shift_amount <= 4
                or not index.shift and index.shift_amount):
            return None
        value &= 0xFFFFFFFF
        if index.extend == "sxtw" and value & (1 << 31):
            value -= 1 << 32
    elif not index.extend:
        if (source.value == "sp" or not (full_width_gpr(index.value) or index.value == "xzr")
                or index.shift not in {"", "lsl"} or not 0 <= index.shift_amount < 64
                or not index.shift and index.shift_amount):
            return None
    else:
        return None
    value = (value << index.shift_amount) & ((1 << 64) - 1)
    return value - (1 << 64) if value & (1 << 63) else value


def stack_fact_slots(state):
    traced = {slot for slot, value in state.stack.items() if tainted(value)}
    protected = traced | {
        slot for mapping in (state.stack_aliases, state.stack_returns)
        for slot, value in mapping.items() if tainted(value)
    } | set(state.stack_images) | set(state.stack_signatures)
    return traced, protected


def shift_pointer_offset(value, delta):
    return value if value is None or value is OPAQUE else value + delta


def opaque_load_register(name):
    return full_width_gpr(name) or (isinstance(name, str) and name.startswith("w")
                                    and reg(name) in {f"x{i}" for i in range(31)}) or name in {"xzr", "wzr"}


def copied_stack_facts(state, source, destination, size):
    """Transactional eight-byte fact transfer, not native object validity proof.

    source=None means an independently proved disjoint image read. Any partial
    meaningful fact is refused; CLEAN is not a byte-value or pointer-origin fact.
    """
    if (type(size) is not int or not 0 < size <= MAX_BYTE_COPY or
            type(destination) is not int or not state.sp <= destination < destination + size <= 0 or
            abs(destination) > 1 << 20 or
            source is not None and (type(source) is not int or
                                    not state.sp <= source < source + size <= 0 or abs(source) > 1 << 20)):
        raise MetadataUnavailable("byte-memory-read-write-ranges-unproved")
    domains = ("stack", "stack_aliases", "stack_returns", "stack_images", "stack_signatures")
    snapshots = {name: dict(getattr(state, name)) for name in domains}
    keys = set().union(*(mapping.keys() for mapping in snapshots.values()))
    if len(keys) > MAX_STACK_SLOTS:
        raise MetadataUnavailable("byte-memory-stack-slot-limit")
    meaningful = {key for key in keys if any(
        key in mapping and (name in {"stack_images", "stack_signatures"} or mapping[key] != CLEAN)
        for name, mapping in snapshots.items())}
    for begin in (source, destination):
        if begin is None:
            continue
        for key in meaningful:
            if begin < key + 8 and key < begin + size and not begin <= key <= begin + size - 8:
                raise MetadataUnavailable("byte-memory-partial-protected-fact")
    post = state.copy()
    for name, snapshot in snapshots.items():
        mapping = getattr(post, name)
        for key in list(mapping):
            if destination < key + 8 and key < destination + size:
                del mapping[key]
        if source is not None:
            for key, value in snapshot.items():
                if source <= key <= source + size - 8:
                    mapping[destination + key - source] = value
    # Overwritten native-spill coverage is no longer an exact eight-byte store.
    post.native_stack_rewritten.difference_update(
        key for key in tuple(post.native_stack_rewritten)
        if destination < key + 8 and key < destination + size)
    if len(set().union(*(getattr(post, name).keys() for name in domains))) > MAX_STACK_SLOTS:
        raise MetadataUnavailable("byte-memory-stack-slot-limit")
    return post


def caller_prologue(decode, root, caller, *, helper_evidence=None):
    """Model only a straight-line prologue from an independently proved entry.

    The caller must supply the entry metadata proof. This routine never searches
    backward from a BL or infers that an adjacent instruction was executed.
    """
    if root & 3 or caller & 3 or not 0 <= caller - root <= 4 * MAX_CALLER_PRELUDE:
        raise MetadataUnavailable("caller prelude alignment/instruction bound")
    state = State()
    helpers = []
    helper_steps = 0

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
        if op == "bl" and len(operands) == 1 and operands[0].kind == "imm":
            lookup = getattr(decode, "frame_prologue", None)
            effects = lookup(operands[0].value) if lookup is not None else None
            if effects is None:
                raise MetadataUnavailable("caller prelude unsupported outlined frame")
            helper_steps += effects.body_size // 4
            if ((caller - root) // 4 + helper_steps > MAX_CALLER_PRELUDE
                    or state.sp & 15 or state.sp >= 0
                    or max(root, effects.entry) < min(caller, effects.entry + effects.body_size)):
                raise MetadataUnavailable("caller prelude outlined frame context/bound")
            for name, offset in effects.saves:
                slot = state.sp + offset
                if (slot < state.sp or slot + 8 > 0 or slot & 7
                        or abs(slot) > 1 << 20
                        or slot not in state.stack and len(state.stack) >= MAX_STACK_SLOTS):
                    raise MetadataUnavailable("caller prelude outlined frame save bound")
                state.stack[slot] = CLEAN
                state.stack_aliases[slot] = state.alias(name)
                for mapping in (state.stack_returns, state.stack_images, state.stack_signatures):
                    mapping.pop(slot, None)
            assign("x29", frozenset({state.sp}))
            # BL replaces LR; unknown entry LR must never survive this call.
            for mapping in (state.registers, state.aliases, state.return_values,
                            state.return_signatures, state.image_addresses, state.constants):
                mapping.pop("x30", None)
            state.zero_upper.discard("x30")
            helpers.append({"call": hex(pc), "entry": hex(effects.entry),
                            "body_size": effects.body_size,
                            "body_sha256": effects.body_sha256,
                            "instructions": effects.body_size // 4})
            continue
        if op in {"nop", "bti"}:
            continue
        if op in {"paciasp", "pacibsp", "autiasp", "autibsp"}:
            # A sized symbol/FDE proves no value for the outer caller's LR.
            # Do not turn an untracked signed return into an unsigned fact.
            raise MetadataUnavailable("caller prelude untracked authenticated return")
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
    if helper_evidence is not None:
        helper_evidence.extend(helpers)
    return state


class Audit:
    def __init__(self, decode, *, max_steps=MAX_STEPS, max_contexts=MAX_CONTEXTS,
                 track_opaque_loads=False):
        self.decode = decode
        self.max_steps = max_steps
        self.max_contexts = max_contexts
        self.track_opaque_loads = track_opaque_loads

    def site(self, address, caller=None, *, entry_context=False):
        if entry_context and caller is not None:
            raise ValueError("entry context and direct caller context are mutually exclusive")
        reasons, accesses, callees = set(), set(), set()
        plt_bindings = {}
        callee_effects = {}
        owned_image_accesses = set()
        return_authentication = set()
        opaque_loads = set()
        states = {}
        pending = deque()
        steps = 0
        stack_windows = {"thread_pointer": None, "protected_facts": None}
        image_stack_separation = set()
        managed_malloc_observations = set()
        jump_tables = {}
        byte_copy_observations = set()
        relocated_pointer_loads = {}
        indirect_edges = {}

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
            # Ownership alone is not stack disjointness. Keep this observation
            # independent of image_read_disjoint, which also requires a trusted
            # runtime admission/exclusion record and covered protected facts.

        def adjust_image_address(origin, delta):
            if origin is None:
                return None
            if hasattr(self.decode, "adjust_image_address"):
                return self.decode.adjust_image_address(origin, delta)
            value = origin + delta
            return value if 0 <= value < 1 << 64 else None

        def observe_stack_facts(state):
            traced, protected = stack_fact_slots(state)
            for kind, slots in (("thread_pointer", traced), ("protected_facts", protected)):
                if not slots:
                    continue
                # These are the analyzer's eight-byte saved facts, not all
                # memory accesses or a complete footprint of unresolved paths.
                low = min(slots) - anchor_sp
                high = max(slots) + 8 - anchor_sp
                previous = stack_windows[kind]
                stack_windows[kind] = (
                    (low, high) if previous is None else
                    (min(low, previous[0]), max(high, previous[1]))
                )

        def image_read_disjoint(pc, state, start, width):
            if (mrs_window is None or type(start) is not int or type(width) is not int or width <= 0
                    or not hasattr(self.decode, "owned_image_data")
                    or self.decode.owned_image_data(start, width) is not True):
                return False
            traced, protected = stack_fact_slots(state)
            if not traced:
                return False
            low, high = min(protected) - anchor_sp, max(protected) + 8 - anchor_sp
            if low < -mrs_window["before"] or high > mrs_window["after"]:
                return False
            # Conditional first-party contract: the recorded default accessor
            # enforces this window, and live Image/stack reservations exclude
            # one another. This waives reads only, never arbitrary writes/RMW.
            image_stack_separation.add((pc, start, width, low, high))
            return True

        def enqueue(pc, returns, state):
            observe_stack_facts(state)
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
        mrs_window = self.decode.mrs_stack_window(address) if hasattr(self.decode, "mrs_stack_window") else None
        initial = State()
        caller_frame = None
        entry_frame = None
        returns = ()
        if entry_context:
            if hasattr(self.decode, "caller_frame"):
                prepared, entry_frame = self.decode.caller_frame(address)
                entry_frame = {**entry_frame, "scope": "conditional straight-line function-entry path to MRS; alternate entries and initial stack origin unproved"}
                if prepared is not None:
                    initial = prepared
                else:
                    problem(address, "entry-prelude:" + entry_frame["reason"])
            else:
                problem(address, "entry-prelude:independent-entry-metadata-unavailable")
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
        anchor_sp = initial.sp
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
            live = state.native_memory_may_contain_tp or any(
                tainted(value)
                for value in (*state.registers.values(), *state.stack.values())
            )
            if instruction is None:
                problem(pc, "undecodable-or-outside-executable-bytes")
                continue
            if pc == address or instruction.tpidr_read:
                # The runtime MRS replacement uses native stack scratch.
                state.native_stack_rewritten.clear()
                destination = instruction.operands[0].value
                state.set(destination, frozenset({0}))
                state.set_alias(destination, CLEAN)
                if reg(destination) == "x29":
                    state.fp = None
                enqueue(pc + 4, returns, state)
                continue
            jump = (self.decode.byte_jump_table(pc)
                    if instruction.mnemonic in {"cmp", "ubfx", "lsr"} and hasattr(self.decode, "byte_jump_table") else None)
            if jump is not None:
                if jump.index_extract is not None:
                    # The W bitfield extract itself bounds the index. No CMP,
                    # default branch, or condition-flag clobber is implied.
                    sources = (jump.index_source, jump.interleaved_and[1])
                    if any(tainted(state.value(name)) or tainted(state.alias(name)) or
                           tainted(state.control(name)) or reg(name) in state.return_signatures or
                           state.image(reg(name)) is not None for name in sources):
                        problem(pc, "jump-table-extract-or-mask-has-pointer-or-control-fact")
                        continue
                    observe_owned_image_access(pc, jump.table_address, len(jump.table_bytes), True, False)
                    if ((state.native_memory_may_contain_tp or any(tainted(v) for v in state.stack.values())) and
                            not image_read_disjoint(pc, state, jump.table_address, len(jump.table_bytes))):
                        problem(pc, "jump-table-load-may-alias-thread-pointer-stack-spill")
                        continue
                    extracted = state.scalar(jump.index_source)
                    lsb, width = jump.index_extract
                    selected = (range(len(jump.targets)) if extracted is None else
                                (((extracted >> lsb) & ((1 << width) - 1)),))
                    masked_destination, masked_source, mask = jump.interleaved_and
                    masked = state.scalar(masked_source)
                    state.set(masked_destination, CLEAN)
                    state.set_alias(masked_destination, CLEAN)
                    state.set_control(masked_destination, CLEAN)
                    state.set_signature(masked_destination, None)
                    state.set_image(masked_destination, None)
                    state.set_scalar(masked_destination, None if masked is None else masked & mask)
                    jump_tables[pc] = {
                        "entry": hex(pc), "kind": "bounded-extract-readonly-byte-jump-table",
                        "body_sha256": jump.body_sha256, "table_sha256": jump.table_sha256,
                        "table": hex(jump.table_address), "bytes": len(jump.table_bytes),
                        "index_extract": jump.index_extract, "interleaved_and": jump.interleaved_and,
                        "preserves_nzcv": True, "default": None,
                        "targets": [hex(target) for target in sorted(set(jump.targets))],
                        "scope": "explicit trusted sealed-Image bytes; normal/owned synchronous lifetime only",
                    }
                    # Preserve entry/index correlation, including duplicate targets.
                    for index in selected:
                        target = jump.targets[index]
                        post = state.copy()
                        for name in (jump.index_register, jump.table_register, jump.offset_register, jump.branch_register):
                            post.set(name, CLEAN)
                            post.set_alias(name, CLEAN)
                        post.set_scalar("w" + jump.index_register[1:], index)
                        post.set_scalar(jump.offset_register, jump.table_bytes[index])
                        post.set_image(jump.table_register, jump.table_address)
                        post.set_image(jump.branch_register, target)
                        enqueue(target, returns, post)
                    continue
                # Only entry at the exact CMP acquires the unsigned bound.
                # A proved interleaved MOV executes before both outcomes.
                # Default executes no table access or dispatch scratch writes.
                state.set("nzcv", CLEAN)
                if jump.prebranch_move is not None:
                    destination, source = jump.prebranch_move
                    value, alias, control, signature, image, scalar = (
                        state.value(source), state.alias(source), state.control(source),
                        state.signature(source), state.image(source), state.scalar(source),
                    )
                    zero_upper = reg(source) in state.zero_upper
                    state.set(destination, value)
                    state.set_alias(destination, alias)
                    state.set_control(destination, control)
                    state.set_signature(destination, signature)
                    state.set_image(destination, image)
                    state.set_scalar(destination, scalar)
                    if zero_upper:
                        state.zero_upper.add(reg(destination))
                enqueue(jump.default_target, returns, state.copy())
                source = jump.index_source
                if (tainted(state.value(source)) or tainted(state.alias(source))
                        or state.signature(source) is not None
                        or reg(source) in state.return_signatures):
                    problem(pc, "jump-table-index-has-pointer-or-signature-fact")
                    continue
                if (jump.requires_zero_extended_index
                        and reg(source) not in state.zero_upper
                        and not (state.scalar(jump.index_register) is not None
                                 and state.scalar(jump.index_register) <= 0xFFFFFFFF)):
                    problem(pc, "jump-table-index-upper-bits-unproved")
                    continue
                observe_owned_image_access(pc, jump.table_address, len(jump.table_bytes), True, False)
                if ((state.native_memory_may_contain_tp or any(tainted(v) for v in state.stack.values()))
                        and not image_read_disjoint(pc, state, jump.table_address, len(jump.table_bytes))):
                    problem(pc, "jump-table-load-may-alias-thread-pointer-stack-spill")
                    continue
                jump_tables[pc] = {
                    "entry": hex(pc), "kind": "bounded-readonly-byte-jump-table",
                    "body_sha256": jump.body_sha256, "table_sha256": jump.table_sha256,
                    "table": hex(jump.table_address), "bytes": len(jump.table_bytes),
                    "default": hex(jump.default_target),
                    "prebranch_move": jump.prebranch_move,
                    "zero_extended_index_required": jump.requires_zero_extended_index,
                    "targets": [hex(target) for target in sorted(set(jump.targets))],
                    "scope": "explicit trusted sealed-Image bytes; normal/owned synchronous lifetime only",
                }
                for target in sorted(set(jump.targets)):
                    post = state.copy()
                    for name in (jump.index_register, jump.table_register, jump.offset_register, jump.branch_register):
                        if jump.requires_zero_extended_index and name == jump.index_register:
                            continue  # This variant reads an already-extended index.
                        post.set(name, CLEAN)
                        post.set_alias(name, CLEAN)
                    post.set_image(jump.table_register, jump.table_address)
                    post.set_image(jump.branch_register, target)
                    enqueue(target, returns, post)
                continue
            summary = (
                self.decode.atomic32(pc)
                if instruction.mnemonic == "bti" and hasattr(self.decode, "atomic32")
                else None
            )
            if summary is None and instruction.mnemonic == "bti" and hasattr(self.decode, "cas64"):
                summary = self.decode.cas64(pc)
            if summary is not None:
                # Atomic helper writes are not exact ordinary stack stores.
                state.native_stack_rewritten.clear()
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
                # The helper's capability flag is also a memory read. An ELF
                # ownership check does not prove separation from initial SP.
                if ((state.native_memory_may_contain_tp or any(tainted(value) for value in state.stack.values())) and
                        not image_read_disjoint(pc, state, summary.flag_address, summary.flag_width)):
                    problem(pc, "atomic-flag-load-may-alias-thread-pointer-stack-spill")
                if (
                    not tainted(base)
                    and not tainted(aliases)
                ):
                    observe_owned_image_access(pc, image, summary.rmw_width, True, True)
                if slot is None:
                    if state.native_memory_may_contain_tp or any(tainted(value) for value in state.stack.values()):
                        problem(
                            pc, "unmodelled-load-may-alias-thread-pointer-stack-spill"
                        )
                    for saved in state.stack_returns:
                        state.stack_returns[saved] = CLEAN
                    for saved in state.stack_aliases:
                        state.stack_aliases[saved] = CLEAN
                    state.stack_images.clear()
                    state.stack_signatures.clear()
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
                    for saved in list(state.stack_signatures):
                        if slot < saved + 8 and saved < slot + summary.rmw_width:
                            del state.stack_signatures[saved]
                if (slot is not None and state.native_stack_clobber_below is not None
                        and slot < state.native_stack_clobber_below):
                    problem(pc, "native-callee-stack-spill-atomic-access-unproved")
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
                    if binding.get("effect") == "byte-memory":
                        if (not returns or state.signature("x30") is not None or
                                tainted(state.value("x30")) or tainted(state.alias("x30")) or
                                state.control("x30") != frozenset({returns[-1][0]}) or
                                state.sp != returns[-1][1]):
                            problem(pc, "byte-memory-return-continuation-unproved")
                            continue
                        continuation = returns[-1][0]
                        call = self.decode(continuation - 4)
                        if (call is None or call.mnemonic != "bl" or
                                call.operands != (Operand("imm", pc),)):
                            problem(pc, "byte-memory-direct-bl-unproved")
                            continue
                        # The summary starts before the PLT's GOT load. Its
                        # native target slot needs independent image/stack separation.
                        read_slot = binding.get("read_slot")
                        _, protected = stack_fact_slots(state)
                        low = min(protected) - anchor_sp if protected else 0
                        high = max(protected) + 8 - anchor_sp if protected else 0
                        if state.native_memory_may_contain_tp:
                            problem(pc, "byte-memory-latent-native-copies-unproved")
                            continue
                        if (mrs_window is None or type(read_slot) is not int or
                                not 0 <= read_slot <= (1 << 64) - 8 or
                                not hasattr(self.decode, "owned_image_data") or
                                self.decode.owned_image_data(read_slot, 8) is not True or
                                low < -mrs_window["before"] or high > mrs_window["after"]):
                            problem(pc, "byte-memory-plt-read-separation-unproved")
                            continue
                        image_stack_separation.add((pc, read_slot, 8, low, high))
                        observe_owned_image_access(pc, read_slot, 8, True, False)
                        size = state.scalar("x2")
                        if (type(size) is not int or not 0 <= size <= MAX_BYTE_COPY or tainted(state.value("x2")) or
                                tainted(state.alias("x2")) or tainted(state.control("x2")) or
                                state.signature("x2") is not None or state.image("x2") is not None):
                            problem(pc, "byte-memory-size-unproved-or-bound")
                            continue
                        post = state.copy()
                        source_kind, source, destination = "unused", 0, 0
                        if size:
                            def pointer(name, state, size):
                                aliases = state.alias(name)
                                image = state.image(name)
                                if (tainted(state.value(name)) or tainted(state.control(name)) or
                                        state.signature(name) is not None):
                                    return None
                                if aliases != CLEAN:
                                    if len(aliases) != 1 or image is not None:
                                        return None
                                    start = next(iter(aliases))
                                    if type(start) is not int or not state.sp <= start < start + size <= 0:
                                        return None
                                    if abs(start) > 1 << 20:
                                        return None
                                    return "stack", start
                                if (image is not None and 0 <= image < image + size <= 1 << 64 and
                                        hasattr(self.decode, "owned_image_data") and
                                        self.decode.owned_image_data(image, size) is True):
                                    return "image", image
                                return None
                            dst, src = pointer("x0", state, size), pointer("x1", state, size)
                            if dst is None or src is None or dst[0] != "stack":
                                problem(pc, "byte-memory-read-write-ranges-unproved")
                                continue
                            source_kind, source = src
                            destination = dst[1]
                            if (src[0] == "stack" and state.native_stack_clobber_below is not None and
                                    source < state.native_stack_clobber_below):
                                problem(pc, "byte-memory-native-stack-read-unproved")
                                continue
                            if src[0] == "image":
                                if not image_read_disjoint(pc, state, source, size):
                                    problem(pc, "byte-memory-image-read-separation-unproved")
                                    continue
                                observe_owned_image_access(pc, source, size, True, False)
                            elif (binding["abi"] == "memory/byte-copy-v1" and
                                  source < destination + size and destination < source + size):
                                problem(pc, "byte-memory-memcpy-overlap")
                                continue
                            try:
                                post = copied_stack_facts(state, source if src[0] == "stack" else None,
                                                          destination, size)
                            except MetadataUnavailable as error:
                                problem(pc, str(error))
                                continue
                        # PLT uses x16/x17 before the leaf; neither is scalar zero.
                        for name in ("x3", "x4", "x5", "x6", "x16", "x17"):
                            post.set(name, CLEAN)
                            post.set_alias(name, CLEAN)
                        for name in ("x3", "x4", "x5", "x6"):
                            post.set_scalar(name, 0)
                        if size:
                            post.set("nzcv", CLEAN)
                            post.set_alias("nzcv", CLEAN)
                        callee_effects[pc] = {
                            "entry": hex(pc), "kind": "conditional-byte-memory",
                            "binding": dict(binding), "maximum_bytes": MAX_BYTE_COPY,
                            "scope": "proved relative ranges/facts on normal returns; valid objects and synchronous lifetime assumed",
                        }
                        byte_copy_observations.add((pc, source_kind, source, destination, size, continuation))
                        enqueue(continuation, returns[:-1], post)
                        continue
                    if binding.get("effect") == "managed-malloc":
                        if mrs_window is None:
                            problem(pc, "managed-malloc-mrs-window-unproved")
                            continue
                        if (not returns or state.signature("x30") is not None or
                                state.control("x30") != frozenset({returns[-1][0]}) or
                                state.sp != returns[-1][1]):
                            problem(pc, "managed-malloc-return-continuation-unproved")
                            continue
                        call = self.decode(returns[-1][0] - 4)
                        if (call is None or call.mnemonic != "bl" or
                                call.operands != (Operand("imm", pc),)):
                            problem(pc, "managed-malloc-direct-bl-unproved")
                            continue
                        if (tainted(state.value("x0")) or tainted(state.alias("x0")) or
                                tainted(state.control("x0")) or state.signature("x0") is not None):
                            problem(pc, "managed-malloc-size-has-pointer-or-control-fact")
                            continue
                        if any(
                            not full_width_gpr(name) and name != "sp" and tainted(value)
                            for mapping in (state.registers, state.aliases, state.return_values)
                            for name, value in mapping.items()
                        ):
                            problem(pc, "managed-malloc-simd-facts-unmodelled")
                            continue
                        _, protected = stack_fact_slots(state)
                        if any(slot < state.sp or slot - anchor_sp < -mrs_window["before"] or
                               slot + 8 - anchor_sp > mrs_window["after"] for slot in protected):
                            problem(pc, "managed-malloc-protected-stack-facts-outside-active-window")
                            continue
                        return_pc = returns[-1][0]
                        post = state.copy()
                        post.native_stack_rewritten.clear()
                        post.native_stack_clobber_below = max(
                            state.sp, state.native_stack_clobber_below
                            if state.native_stack_clobber_below is not None else state.sp)
                        post.native_memory_may_contain_tp |= any(
                            tainted(value) for value in (*state.registers.values(), *state.stack.values()))
                        poison = OPAQUE_VALUE if post.native_memory_may_contain_tp else CLEAN
                        clear_scratch = binding.get("abi") == guest_allocator_contract.MALLOC_CLEAR_SCRATCH_ABI
                        for index in (*range(19), 30):
                            # Clobbered does not mean independent of TP: an
                            # unspecified scratch result may retain/copy input
                            # bits. V2 adds exact normal-return clearing while
                            # retaining platform x18 and LR uncertainty.
                            clean = index == 0 or (clear_scratch and 1 <= index <= 17)
                            post.set(f"x{index}", CLEAN if clean else poison)
                            post.set_alias(f"x{index}", CLEAN)
                        for index in range(32):
                            for prefix in ("b", "h", "s", "d", "q", "v"):
                                if 8 <= index <= 15 and prefix in ("b", "h", "s", "d"):
                                    continue  # Preserved low 64 bits, checked clean above.
                                # Only this typed malloc handler writes x0;
                                # its x1/q0/q1 result cells stay initialized.
                                # Other vector bytes are scrubbed, or admitted
                                # clean low halves preserved by the ABI.
                                post.set(f"{prefix}{index}", CLEAN if clear_scratch else poison)
                                post.set_alias(f"{prefix}{index}", CLEAN)
                        callee_effects[pc] = {
                            "entry": hex(pc), "kind": "conditional-managed-malloc",
                            "binding": dict(binding), "assumptions": list(guest_allocator_contract.ASSUMPTIONS),
                            "explicit_cell_admission": "native errno/guest errno/boundary span outside current prepared stack",
                            "result": "opaque; no heap, disjointness or readable-byte fact",
                            "normal_return_scratch_cleared": clear_scratch,
                        }
                        managed_malloc_observations.add((
                            pc, state.sp, return_pc, bool(protected),
                            min(protected) - anchor_sp if protected else 0,
                            max(protected) + 8 - anchor_sp if protected else 0,
                        ))
                        enqueue(return_pc, returns[:-1], post)
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
            if op in {"add", "sub"} and len(operands) == 3 and operands[2].kind == "reg":
                delta = exact_register_delta(operands, state)
                if delta is None:
                    if any(item.kind == "reg" and (
                            tainted(state.value(item.value)) or tainted(state.alias(item.value))
                            or state.image(item.value) is not None or state.signature(item.value) is not None
                    ) for item in operands[1:]):
                        problem(pc, "register-arithmetic-index-unproved")
                        continue
                    # Unknown scalar arithmetic with no address facts still
                    # follows the existing generic clobber transfer below.
                    # It cannot derive an affine address or exact constant.
                else:
                    operands = (*operands[:2], Operand("imm", delta))
            elif any(item.shift or item.shift_amount or item.extend for item in operands):
                problem(pc, "unmodelled-operand-shift-or-extension")
                continue
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
            if op == "msr" and (state.return_signatures or state.stack_signatures):
                # An understood path must not silently assume authentication
                # keys/system state stayed fixed. Even FP-only MSR forms need
                # a separately verified effect before crossing this barrier.
                problem(pc, "return-auth-system-state-write")
                state.return_signatures.clear()
                state.stack_signatures.clear()
                continue
            if op == "xpaclri":
                problem(pc, "unmodelled-return-authentication:xpaclri")
                state.set("x30", CLEAN)
                continue
            if op in {"paciasp", "pacibsp", "autiasp", "autibsp"}:
                profile = getattr(self.decode, "return_auth_profile", None)
                if (
                    not isinstance(profile, dict)
                    or type(profile.get("feature_pauth")) is not int
                    or profile["feature_pauth"] != 1
                    or type(profile.get("feature_pauth_lr")) is not int
                    or profile["feature_pauth_lr"] != 0
                ):
                    problem(pc, "return-auth-profile-unproved")
                    continue
                if operands:
                    problem(pc, "unsupported-return-authentication-form")
                    continue
                if tainted(state.value("x30")) or tainted(state.alias("x30")):
                    problem(pc, "return-authentication-of-traced-pointer")
                    continue
                key = "A" if op in {"paciasp", "autiasp"} else "B"
                signature = state.signature("x30")
                if op in {"paciasp", "pacibsp"}:
                    if signature is not None:
                        problem(pc, "return-auth-double-sign")
                        continue
                    tokens = state.control("x30")
                    if not tokens or any(type(token) is not int or not 0 <= token < 1 << 64
                                         for token in tokens):
                        problem(pc, "return-auth-missing-unsigned-token")
                        continue
                    state.set("x30", CLEAN)
                    state.set_signature("x30", ReturnSignature(tokens, state.sp, key))
                else:
                    if signature is None:
                        problem(pc, "return-auth-missing-signature")
                        continue
                    if signature.key != key or signature.sp != state.sp:
                        problem(pc, "return-auth-key-or-sp-mismatch")
                        continue
                    tokens = signature.tokens
                    state.set("x30", CLEAN)
                    state.set_control("x30", tokens)
                return_authentication.add((pc, op, key, state.sp, tuple(sorted(tokens))))
                enqueue(next_pc, returns, state)
                continue
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
                if state.signature(return_register) is not None:
                    problem(pc, "authenticated-return-not-authenticated")
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
                elif entry_context and state.sp != 0:
                    problem(pc, "entry-context-stack-not-restored-at-return")
                elif state.native_memory_may_contain_tp:
                    problem(pc, "native-callee-spills-return-needs-caller-context")
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
                target = None
                entry = None
                if op in {"blr", "br"} and len(operands) == 1 and operands[0].kind == "reg":
                    name = operands[0].value
                    if (full_width_gpr(name) and not tainted(state.value(name)) and
                            not tainted(state.alias(name)) and not tainted(state.control(name)) and
                            state.signature(name) is None):
                        target = state.image(name)
                        if target is not None and hasattr(self.decode, "indirect_function_entry"):
                            entry = self.decode.indirect_function_entry(target)
                if entry is None:
                    problem(pc, "indirect-call-or-branch-with-unproved-summary")
                    continue
                indirect_edges[(pc, target, op)] = entry
                if op == "blr":
                    if len(returns) == MAX_CALL_DEPTH:
                        problem(pc, "callee-depth-limit")
                        continue
                    state.set("x30", CLEAN)
                    state.set_alias("x30", CLEAN)
                    state.set_control("x30", frozenset({next_pc}))
                    callees.add((pc, target))
                    enqueue(target, returns + ((next_pc, state.sp),), state)
                else:
                    enqueue(target, returns, state)
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
                    signature = state.signature(source.value)
                    if width == "w":
                        if tainted(value):
                            problem(pc, "narrowed-thread-pointer")
                        if tainted(alias):
                            problem(pc, "narrowed-stack-address-unmodelled")
                        value = alias = control = CLEAN
                        if reg(source.value) in state.return_signatures:
                            problem(pc, "narrowed-authenticated-return")
                    post.set(destination.value, value)
                    post.set_alias(destination.value, alias)
                    post.set_control(destination.value, control)
                    post.set_signature(destination.value, signature if width == "x" else None)
                    post.set_image(destination.value, image if width == "x" else None)
                    if reg(destination.value) == "x29":
                        post.fp = next(iter(alias)) if len(alias) == 1 and None not in alias else None
                    enqueue(next_pc, returns, post)
                continue
            if op == "sxtw":
                # Capstone 5 can include the overwritten X destination in
                # regs_access(). The architectural input is only Wn. Never
                # mistake an old destination fact for an input dependency.
                if (len(operands) != 2 or any(item.kind != "reg" for item in operands)
                        or not (full_width_gpr(operands[0].value) or operands[0].value == "xzr")
                        or not operands[1].value.startswith("w")
                        or reg(operands[1].value) not in {f"x{i}" for i in range(31)} | {"xzr"}):
                    problem(pc, "unsupported-sxtw-form")
                    continue
                destination, source = operands
                value = state.value(source.value)
                if tainted(value):
                    problem(pc, "narrowed-thread-pointer")
                if tainted(state.alias(source.value)):
                    problem(pc, "narrowed-stack-address-unmodelled")
                if reg(source.value) in state.return_signatures:
                    problem(pc, "narrowed-authenticated-return")
                state.set(destination.value, OPAQUE_VALUE if tainted(value) else CLEAN)
                state.set_alias(destination.value, CLEAN)
                if reg(destination.value) == "x29":
                    state.fp = None
            elif op == "mov" and len(operands) == 2 and operands[0].kind == "reg":
                destination, source = operands
                scalar = (state.scalar(source.value) if source.kind == "reg"
                          else source.value if source.kind == "imm" else None)
                image = state.image(source.value) if source.kind == "reg" else None
                control = state.control(source.value) if source.kind == "reg" else CLEAN
                signature = state.signature(source.value) if source.kind == "reg" else None
                if destination.value.startswith("w"):
                    control = CLEAN
                    if source.kind == "reg" and reg(source.value) in state.return_signatures:
                        problem(pc, "narrowed-authenticated-return")
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
                state.set_signature(destination.value, signature if full_width_gpr(destination.value) else None)
                state.set_image(destination.value, image)
                state.set_scalar(destination.value, scalar)
            elif (
                op in {"add", "sub"}
                and len(operands) == 3
                and operands[2].kind == "imm"
            ):
                destination, source, immediate = operands
                scalar = state.scalar(source.value) if source.kind == "reg" else None
                # W-register arithmetic zero-extends a truncated result. It
                # cannot preserve the full-width TP-relative address even
                # when the immediate is zero.
                if destination.value.startswith("w") and tainted(values[1]):
                    problem(pc, "narrowed-thread-pointer")
                    continue
                if OPAQUE in values[1]:
                    problem(pc, "opaque-thread-pointer-arithmetic")
                    continue
                if source.kind == "reg" and reg(source.value) in state.return_signatures:
                    problem(pc, "authenticated-return-arithmetic")
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
                        shift_pointer_offset(offset, delta)
                        for offset in values[1]
                    )
                    if any(
                        offset is not None and offset is not OPAQUE and abs(offset) > 4096
                        for offset in value
                    ):
                        problem(pc, "pointer-offset-limit")
                        continue
                    state.set(destination.value, value)
                if full_width_gpr(destination.value) and image is not None:
                    state.set_image(destination.value, adjust_image_address(image, delta))
                state.set_scalar(destination.value, None if scalar is None else scalar + delta)
            elif op in {"ldr", "ldrsw"} and len(operands) == 2 and operands[1].kind != "mem":
                # Capstone represents a PC-relative literal address as an
                # immediate, not a memory operand. It still reads memory and
                # may alias a saved TP if initial stack origin is unproved.
                if operands[0].kind != "reg" or operands[1].kind != "imm":
                    problem(pc, "unsupported-literal-memory-form")
                    continue
                observe_owned_image_access(pc, operands[1].value, instruction.width, True, False)
                value = CLEAN
                if ((state.native_memory_may_contain_tp or any(tainted(saved) for saved in state.stack.values())) and
                        not image_read_disjoint(pc, state, operands[1].value, instruction.width)):
                    if (self.track_opaque_loads and instruction.width in (1, 2, 4, 8)
                            and opaque_load_register(operands[0].value)):
                        value = OPAQUE_VALUE
                        opaque_loads.add((pc, operands[0].value, instruction.width))
                    else:
                        problem(pc, "unmodelled-literal-load-may-alias-thread-pointer-stack-spill")
                state.set(operands[0].value, value)
                state.set_alias(operands[0].value, CLEAN)
                if reg(operands[0].value) == "x29":
                    state.fp = None
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
                image_disjoint = False
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
                    if load:
                        image_disjoint = image_read_disjoint(
                            pc, state, image_base + memory.displacement, instruction.width * len(data))
                # CLEAN means "not traced to this MRS", not "cannot alias
                # our stack". A preexisting unknown argument may point at a
                # spilled TP even though exact materialized aliases are tracked.
                # Only singleton known addresses can bypass this uncertainty.
                possible_spill_load = (
                    load
                    and slot is None
                    and not tainted(base_value)
                    and not tainted(index_value)
                    and not image_disjoint
                    and (state.native_memory_may_contain_tp or any(tainted(value) for value in state.stack.values()))
                )
                if (load and slot is not None and state.native_stack_clobber_below is not None
                        and slot < state.native_stack_clobber_below
                        and not (instruction.width == 8 and data and all(
                            slot + index * 8 in state.native_stack_rewritten
                            for index in range(len(data))))):
                    problem(pc, "native-callee-stack-spill-reload-unproved")
                opaque_source = False
                if possible_spill_load:
                    if (self.track_opaque_loads and data and instruction.width in (1, 2, 4, 8)
                            and all(opaque_load_register(item.value) for item in data)):
                        opaque_source = True
                        for item in data:
                            opaque_loads.add((pc, item.value, instruction.width))
                    else:
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
                        elif offset is OPAQUE:
                            problem(pc, "opaque-thread-pointer-memory-base")
                            accesses.add((pc, OPAQUE, op, "unsupported"))
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
                    state.native_stack_rewritten.clear()
                    # Even a clean payload can partially overwrite a TP spill.
                    # Its old affine value is no longer an exact reload fact.
                    # Keep the failure sticky rather than silently laundering
                    # the potentially modified pointer into a guard address.
                    if state.native_memory_may_contain_tp or any(tainted(value) for value in state.stack.values()):
                        problem(pc, "unmodelled-store-may-clobber-thread-pointer-stack-spill")
                    for saved in state.stack:
                        state.stack[saved] = CLEAN
                    # Unknown stores can clobber a saved LR even when neither
                    # their address nor payload is derived from this TP.
                    for saved in state.stack_returns:
                        state.stack_returns[saved] = CLEAN
                    for saved in state.stack_aliases:
                        state.stack_aliases[saved] = CLEAN
                    state.stack_images.clear()
                    state.stack_signatures.clear()
                for index, item in enumerate(data):
                    key = None if slot is None else slot + index * instruction.width
                    if store and key is not None:
                        state.native_stack_rewritten.difference_update(
                            saved for saved in tuple(state.native_stack_rewritten)
                            if key < saved + 8 and saved < key + instruction.width)
                        for saved in state.stack_returns:
                            if key < saved + 8 and saved < key + instruction.width:
                                state.stack_returns[saved] = CLEAN
                        for saved in list(state.stack_images):
                            if key < saved + 8 and saved < key + instruction.width:
                                del state.stack_images[saved]
                        for saved in list(state.stack_signatures):
                            if key < saved + 8 and saved < key + instruction.width:
                                del state.stack_signatures[saved]
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
                        if key is not None and not opaque_load_register(item.value) and (
                            tainted(state.stack.get(key, CLEAN))
                            or tainted(state.stack_aliases.get(key, CLEAN))
                        ):
                            problem(pc, "tainted-vector-stack-reload-unmodelled")
                            state.set(item.value, CLEAN)
                            state.set_alias(item.value, CLEAN)
                            continue
                        signature = (
                            state.stack_signatures.get(key)
                            if key is not None and instruction.width == 8
                            else None
                        )
                        if key is not None:
                            for saved in state.stack_signatures:
                                if (key < saved + 8 and saved < key + instruction.width
                                        and (key != saved or instruction.width != 8)):
                                    problem(pc, "partial-overlap-with-authenticated-return")
                        image = (
                            state.stack_images.get(key)
                            if key is not None and instruction.width == 8
                            else None
                        )
                        state.set(
                            item.value,
                            state.stack.get(key, CLEAN) if key is not None
                            else OPAQUE_VALUE if opaque_source else CLEAN,
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
                        state.set_signature(item.value, signature if full_width_gpr(item.value) else None)
                        state.set_image(item.value, image)
                        if (slot is None and load and op == "ldr" and instruction.width == 8 and
                                len(data) == 1 and not instruction.writeback and not memory.index and
                                full_width_gpr(item.value) and image_base is not None and image_disjoint and
                                not state.native_memory_may_contain_tp and
                                hasattr(self.decode, "relocated_code_pointer")):
                            pointer = self.decode.relocated_code_pointer(image_base + memory.displacement)
                            if pointer is not None:
                                state.set_image(item.value, pointer["target"])
                                relocated_pointer_loads[(pc, pointer["slot"], pointer["target"])] = {"load": hex(pc), **pointer}
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
                            if state.native_stack_clobber_below is not None:
                                state.native_stack_rewritten.add(key)
                            signature = state.signature(item.value)
                            if signature is not None:
                                state.stack_signatures[key] = signature
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
                                shift_pointer_offset(value, delta)
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
                                shift_pointer_offset(value, delta)
                                for value in base_value
                            ),
                        )
                        state.set_image(
                            memory.base,
                            adjust_image_address(image_base, delta),
                        )
            else:
                if any(reg(name) in state.return_signatures for name in instruction.reads):
                    problem(pc, "unmodelled-authenticated-return-consumer:" + op)
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
            "entry_frame": entry_frame,
            "opaque_load_tracking": self.track_opaque_loads,
            "opaque_loads": [{"pc": hex(pc), "destination": destination, "width": width,
                              "scope": "possible derived bits, never an affine TP or return address"}
                             for pc, destination, width in sorted(opaque_loads)],
            "classification": classification,
            "steps": steps,
            "analysis_strategy": "bounded-exact-state-partitions",
            "state_partitions": len(states),
            "mrs_window_record": mrs_window,
            "conditional_image_stack_separation": [
                {"pc": hex(pc), "analysis_image_address": hex(start), "read_width": width,
                 "protected_min_offset": low, "protected_end_offset": high}
                for pc, start, width, low, high in sorted(image_stack_separation)
            ],
            "relocated_pointer_loads": [record for _, record in sorted(relocated_pointer_loads.items())],
            "indirect_edges": [{"pc": hex(pc), "target": hex(target), "operation": op,
                                "function": entry}
                               for (pc, target, op), entry in sorted(indirect_edges.items())],
            "conditional_byte_copies": [
                {"entry": hex(pc), "source_kind": kind, "source": source,
                 "destination_stack_offset": destination, "bytes": size,
                 "continuation": hex(continuation)}
                for pc, kind, source, destination, size, continuation in sorted(byte_copy_observations)
            ],
            "conditional_managed_malloc": [
                {"pc": hex(pc), "abstract_call_sp": sp, "continuation": hex(continuation),
                 "has_protected_stack_facts": has_facts,
                 "protected_min_offset": low, "protected_end_offset": high,
                 "scope": "conditional call crossing only; native spill footprint and full lifetime unproved"}
                for pc, sp, continuation, has_facts, low, high in sorted(managed_malloc_observations)
            ],
            "bounded_jump_tables": list(jump_tables.values()),
            "observed_stack_fact_window": {
                "anchor": "first-mrs-sp",
                "abstract_anchor_sp": anchor_sp,
                "fact_width_bytes": 8,
                "runtime_enforced": False,
                "scope": "observed abstract facts only; unresolved paths and complete memory footprint unproved",
                "windows": {
                    kind: None if bounds is None else {"min_offset": bounds[0], "end_offset": bounds[1]}
                    for kind, bounds in stack_windows.items()
                },
            },
            "accesses": [
                {"pc": hex(pc), "offset": None if offset is OPAQUE else offset,
                 "instruction": op, "kind": kind,
                 **({"value_kind": "opaque-derived"} if offset is OPAQUE else {})}
                for pc, offset, op, kind in sorted(accesses, key=lambda row: (
                    row[0], row[1] is OPAQUE, 0 if row[1] is OPAQUE else row[1], row[2], row[3]))
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
            "return_authentication": [
                {"pc": hex(pc), "instruction": op, "key": key, "sp": sp,
                 "return_tokens": [hex(token) for token in tokens]}
                for pc, op, key, sp, tokens in sorted(return_authentication)
            ],
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

    A sectionless ELF remains unsupported here. Neither
    symbol visibility nor an unversioned name is used to guess namespace binding.
    """

    @property
    def data(self):
        return self._data

    @property
    def elf_sha256(self):
        return self._elf_sha256

    def __init__(self, data, fallback_name, provider_bridge=None):
        if not isinstance(data, (bytes, bytearray)) or not 64 <= len(data) <= MAX_BYTES:
            raise ValueError("binding metadata requires bounded ELF64 AArch64")
        self._data = bytes(data)
        data = self._data
        self._elf_sha256 = hashlib.sha256(data).hexdigest()
        self.loads = []
        self.relocations = {}
        self.all_relocations = {}
        self.relro = None
        self.function_entry_cache = {}
        self.function_entry_work = 0
        self.relocation_writes = set()
        self.static_rodata_safe = True
        self.proof = {}
        self.proof_hash = None
        self.provider_bridge = None
        self.trusted_provider_contracts = False
        self.mrs_window_record = None
        self.version_requirements = None
        self.version_definitions = None
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
            if kind not in {1, 2, 0x6474E550, 0x6474E552}:
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
            elif kind == 0x6474E552:
                if self.relro is not None:
                    raise ValueError("duplicate PT_GNU_RELRO")
                self.relro = (address, memory)
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
                17, 18, 19, 22, 30, 35, 36, 37,
                20,
                23,
                0x6000000F, 0x60000010, 0x60000011, 0x60000012,
                0x6FFFE000, 0x6FFFE001, 0x6FFFE003, 0x6FFFE005,
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
        if (22 in self.tags or self.tags.get(30, 0) & 4 or any(
                tag in self.tags for tag in (17, 18, 19, 35, 36, 37,
                                            0x6000000F, 0x60000010, 0x60000011, 0x60000012,
                                            0x6FFFE000, 0x6FFFE001, 0x6FFFE003, 0x6FFFE005))):
            # Existing binding diagnostics are not a complete relocation
            # parser. They cannot certify original bytes for these encodings.
            self.static_rodata_safe = False
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
                self.all_relocations[slot] = (index, addend, kind)
                if kind not in {257, 1025, 1026, 1027} or slot > (1 << 64) - 8 or not any(
                        flags & 2 and begin <= slot and slot + 8 <= end
                        for begin, end, _, _, flags in self.loads):
                    self.static_rodata_safe = False
                else:
                    self.relocation_writes.add(slot)
                if kind not in {1025, 1026}:
                    continue
                if index >= self.symbol_count or slot & 7:
                    raise ValueError("PLT/GOT relocation symbol/alignment")
                self.offset(slot, 8)
                self.relocations[slot] = (index, addend, kind)

    def function_entry(self, address):
        """Independent sized-symbol/FDE root; no authenticated-branch claim."""
        if type(address) is not int or address < 0 or address & 3:
            return None
        if address in self.function_entry_cache:
            return self.function_entry_cache[address]
        if self.function_entry_work + self.symbol_count > MAX_INDIRECT_SYMBOL_WORK or len(self.function_entry_cache) >= MAX_INDIRECT_ENTRIES:
            return None
        self.function_entry_work += self.symbol_count
        try:
            root, size, source = self.caller_function(address, with_source=True)
            result = ({"entry": hex(root), "size": size, "source": source, "elf_sha256": self.elf_sha256}
                      if root == address else None)
        except MetadataUnavailable:
            result = None
        self.function_entry_cache[address] = result
        return result

    def relocated_code_pointer(self, address):
        """Exact RELATIVE value in conditionally sealed Darwin 16KiB RELRO.

        The selected bridge's normal/owned synchronous Image seal and lifetime
        are trusted explicitly. File bytes alone never grant runtime immutability.
        """
        if (not self.trusted_provider_contracts or not self.static_rodata_safe or
                type(address) is not int or address < 0 or address & 7 or
                address > (1 << 64) - 8 or self.relro is None):
            return None
        begin, size = self.relro
        end = begin + size
        if not size or not begin <= address <= end - 8:
            return None
        # Match Image::seal's host-page constraints, including reservation gaps.
        page_begin, page_end = begin & ~16383, (end + 16383) & ~16383
        if end > ((max(stop for _, stop, _, _, _ in self.loads) + 16383) & ~16383):
            return None
        if not any(flags & 2 and start <= begin < stop for start, stop, _, _, flags in self.loads):
            return None
        for start, stop, _, _, flags in self.loads:
            low, high = max(start, page_begin), min(stop, page_end)
            if low < high and (flags & 1 or flags & 2 and (low < begin or high > end)):
                return None
        if not any(flags & 6 == 6 and not flags & 1 and start <= address <= stop - 8
                   for start, stop, _, _, flags in self.loads):
            return None
        relocation = self.all_relocations.get(address)
        if relocation is None or relocation[0] != 0 or relocation[2] != 1027:
            return None
        if not hasattr(self, "sorted_relocation_slots"):
            self.sorted_relocation_slots = sorted(self.all_relocations)
        slots = self.sorted_relocation_slots
        index = bisect_left(slots, address)
        if ((index and slots[index - 1] + 8 > address) or
                (index + 1 < len(slots) and slots[index + 1] < address + 8)):
            return None
        target = relocation[1]
        entry = self.function_entry(target)
        if entry is None:
            return None
        return {"slot": hex(address), "target": target, "relocation_type": 1027,
                "relro_begin": hex(begin), "relro_size": size, "page_size": 16384,
                "function": entry, "elf_sha256": self.elf_sha256,
                "bridge_sha256": self.provider_bridge["sha256"] if self.provider_bridge else None,
                "binding_proof_sha256": self.proof_hash,
                "scope": "trusted sealed Image in normal/owned synchronous lifetime; no caller/object/PAC proof"}

    def readonly_jump_table_bytes(self, address, size):
        # Conditional normal/owned synchronous sealed-Image contract. The
        # trusted loader seals R/non-W data before constructors; guest mmap
        # services cannot remap Image reservations. Hash identity alone does
        # not grant immutability against arbitrary native mutation/teardown.
        if (not self.trusted_provider_contracts or not self.static_rodata_safe
                or type(address) is not int or type(size) is not int
                or address < 0 or not 0 < size <= 64 or address + size >= 1 << 64):
            return None
        if any(address < slot + 8 and slot < address + size for slot in self.relocation_writes):
            return None
        for begin, _, offset, file_size, flags in self.loads:
            if flags & 7 == 4 and begin <= address and address + size <= begin + file_size:
                start = offset + address - begin
                return self.data[start:start + size]
        return None

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
        self.mrs_window_record = None
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
        if own["elf_sha256"] != self.elf_sha256:
            raise ValueError("binding evidence ELF hash mismatch")
        validated_window = None
        if "mrs_stack_window" in own:
            validated_window = guest_mrs_window.validate_mrs_window_record(self, own["mrs_stack_window"])
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
            # Identity is a diagnostic snapshot, not a native effect grant.
            # Keep legacy adapters unknown; reject malformed claimed identity.
            guest_native_target.validate_native_target(binding)
            guest_allocator_contract.managed_malloc_binding(binding)
            guest_memory_contract.byte_memory_binding(binding)
            self.proof[slot] = dict(binding)
        if self.trusted_provider_contracts:
            self.mrs_window_record = validated_window

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
        if self.version_definitions is not None:
            return self.version_definitions.get(index)
        start = self.tags.get(0x6FFFFFFC, 0)
        count = self.tags.get(0x6FFFFFFD, 0)
        if not start or not count:
            self.version_definitions = {}
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
        self.version_definitions = definitions
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
        if guest_memory_contract.byte_memory_binding(proof):
            if not self.trusted_provider_contracts:
                return {**result, "reason": "byte-memory-contract-untrusted"}
            if (addend != 0 or proof.get("relocation_type") != kind or info & 15 != 2 or
                    (required_library is not None and proof["provider"] != required_library)):
                return {**result, "reason": "byte-memory-binding-mismatch"}
            # This identity permits checking ranges, never bypassing that check.
            return {**result, "effect": "byte-memory", "abi": proof["abi"], "read_slot": slot,
                    "bridge_sha256": self.provider_bridge["sha256"],
                    "reason": "explicit-first-party-byte-memory-contract"}
        if guest_allocator_contract.managed_malloc_binding(proof):
            if not self.trusted_provider_contracts:
                return {**result, "reason": "managed-malloc-contract-untrusted"}
            if (addend != 0 or proof.get("relocation_type") != kind or info & 15 != 2 or
                    (required_library is not None and proof["provider"] != required_library)):
                return {**result, "reason": "managed-malloc-binding-mismatch"}
            return {**result, "effect": "managed-malloc", "abi": proof["abi"],
                    "bridge_sha256": self.provider_bridge["sha256"],
                    "reason": "explicit-first-party-managed-malloc-contract"}
        if proof.get("native_target") is not None:
            if not self.trusted_provider_contracts:
                return {**result, "reason": "native-target-identity-untrusted"}
            if (
                addend != 0
                or proof.get("relocation_type") != kind
                or info & 15 != 2
                or (required_library is not None and proof["provider"] != required_library)
            ):
                return {**result, "reason": "native-target-identity-binding-mismatch"}
            return {
                **result,
                "native_target": guest_native_target.validate_native_target(proof),
                "bridge_sha256": self.provider_bridge["sha256"],
                "reason": "native-target-identity-only-no-effect-contract",
            }
        if (
            proof["provider"] != self.name
            or proof.get("provider_elf_sha256") != self.elf_sha256
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
        import guest_return_auth_profile
        from capstone import arm64

        if capstone.__version__ != "5.0.3":
            raise ValueError("audit requires pinned Capstone 5.0.3")
        self.arm64 = arm64
        if not 64 <= path.stat().st_size <= MAX_BYTES:
            raise ValueError("ELF size bound")
        with path.open("rb") as stream:
            self.data = stream.read(MAX_BYTES + 1)
        library = Path(capstone._cs._name)
        self.return_auth_profile = guest_return_auth_profile.host_return_auth_profile()
        self.identity = {
            "version": capstone.__version__,
            "return_auth_profile": self.return_auth_profile,
            "return_auth_profile_sha256": hashlib.sha256(
                Path(guest_return_auth_profile.__file__).read_bytes()
            ).hexdigest(),
            "mrs_window_reader_sha256": hashlib.sha256(
                Path(guest_mrs_window.__file__).read_bytes()
            ).hexdigest(),
            "native_target_reader_sha256": hashlib.sha256(
                Path(guest_native_target.__file__).read_bytes()
            ).hexdigest(),
            "memory_contract_reader_sha256": hashlib.sha256(
                Path(guest_memory_contract.__file__).read_bytes()
            ).hexdigest(),
            "allocator_contract_reader_sha256": hashlib.sha256(
                Path(guest_allocator_contract.__file__).read_bytes()
            ).hexdigest(),
            "jump_table_reader_sha256": hashlib.sha256(
                Path(guest_jump_table.__file__).read_bytes()
            ).hexdigest(),
            "frame_effects_sha256": hashlib.sha256(
                Path(guest_frame_effects.__file__).read_bytes()
            ).hexdigest(),
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
        self.jump_table_cache = {}
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

    def byte_jump_table(self, address):
        if address in self.jump_table_cache:
            return self.jump_table_cache[address]
        if self.bindings is None:
            return None

        def word(pc):
            raw = self.word(pc)
            return int.from_bytes(raw, "little") if raw is not None else None

        result = guest_jump_table.recognize_byte_jump_table(
            word, address, self.bindings.readonly_jump_table_bytes)
        if result is not None and any(self.word(target) is None for target in (*result.targets,
                *((result.default_target,) if result.default_target is not None else ()))):
            result = None
        if len(self.jump_table_cache) < 1 << 20:
            self.jump_table_cache[address] = result
        return result

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

    def mrs_stack_window(self, address):
        metadata = self.bindings
        record = metadata.mrs_window_record if metadata is not None else None
        if (record is None or record.image_stack_exclusion_profile != 1
                or type(address) is not int or address not in record.sites):
            return None
        return {
            "profile": record.profile, "before": record.before, "after": record.after,
            "image_stack_exclusion_profile": record.image_stack_exclusion_profile,
            "source_site": hex(address), "elf_sha256": metadata.elf_sha256,
            "bridge_sha256": metadata.provider_bridge["sha256"],
            "binding_proof_sha256": metadata.proof_hash,
            "trust": "explicit-first-party-bridge-contract",
            "scope": "producer installation snapshot; explicitly trusted window and live Image/native-stack exclusion contracts",
            "lifetime_scope": "normal-or-owned-synchronous-traces",
            "excludes": ["arbitrary-host-cleanup-epochs", "general-Loader-shutdown-quiescence"],
        }

    def relocated_code_pointer(self, address):
        return self.bindings.relocated_code_pointer(address) if self.bindings is not None else None

    def indirect_function_entry(self, address):
        if not hasattr(self, "entry_metadata"):
            self.entry_metadata = self.bindings or ElfBindingMetadata(self.data, "<indirect-entry>")
        return self.entry_metadata.function_entry(address)

    def frame_prologue(self, address):
        def word(pc):
            raw = self.word(pc)
            return int.from_bytes(raw, "little") if raw is not None else None
        return guest_frame_effects.recognize(word, address)

    def caller_frame(self, caller):
        try:
            metadata = self.bindings or ElfBindingMetadata(
                self.data, "<caller-context>"
            )
            root, size, source = metadata.caller_function(caller, with_source=True)
            helpers = []
            state = caller_prologue(self, root, caller, helper_evidence=helpers)
        except MetadataUnavailable as error:
            return None, {"status": "unknown", "reason": str(error)}
        return state, {
            "status": "validated-entry-context",
            "source": source,
            "entry": hex(root),
            "size": size,
            "instructions": (caller - root) // 4,
            "outlined_helpers": helpers,
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
                shifts = {self.arm64.ARM64_SFT_INVALID: "", self.arm64.ARM64_SFT_LSL: "lsl",
                          self.arm64.ARM64_SFT_LSR: "lsr", self.arm64.ARM64_SFT_ASR: "asr",
                          self.arm64.ARM64_SFT_ROR: "ror"}
                extensions = {self.arm64.ARM64_EXT_INVALID: "", self.arm64.ARM64_EXT_UXTW: "uxtw",
                              self.arm64.ARM64_EXT_SXTW: "sxtw"}
                operands.append(Operand(
                    "reg", decoded.reg_name(item.reg), shift=shifts.get(item.shift.type, "unsupported"),
                    shift_amount=item.shift.value, extend=extensions.get(item.ext, "unsupported")))
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
    parser.add_argument("--track-opaque-loads", action="store_true",
                        help="experimental typed GPR taint for uncertain TP spill loads; vector/atomic uncertainty still refuses")
    parser.add_argument("--entry-context", action="store_true",
                        help="conditional independently validated function-entry prologue to each MRS")
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
        help="explicitly trust this first-party bridge implementation of provider, MRS-window and Image/native-stack exclusion contracts",
    )
    parser.add_argument(
        "--guest-library-dir", type=Path,
        help="explicit directory of all hash-matched recorded guest ELFs; enables bounded cross-ELF body traversal",
    )
    args = parser.parse_args()
    if args.entry_context and args.caller is not None:
        parser.error("--entry-context and --caller are mutually exclusive")
    if args.caller is not None and (not args.site or len(args.site) != 1):
        parser.error("--caller requires exactly one --site")
    if args.provider_bridge is not None and args.bindings is None:
        parser.error("--provider-bridge requires an actual binding report")
    if args.guest_library_dir is not None:
        if args.bindings is None:
            parser.error("--guest-library-dir requires an actual binding report")
        from guest_namespace import NamespaceDecoder

        decoder = NamespaceDecoder(args.elf, args.bindings, args.guest_library_dir,
                                   ElfDecoder, args.provider_bridge)
    else:
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
                decoder, max_steps=min(MAX_STEPS, MAX_TOTAL_STEPS - total_steps),
                track_opaque_loads=args.track_opaque_loads,
            ).site(site, caller=args.caller, entry_context=args.entry_context)
        total_steps += result.get("steps", 0)
        results.append(result)
    report = {
        "schema": 1,
        "experimental_opaque_load_tracking": args.track_opaque_loads,
        "analyzer_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "decoder": "capstone-5.0.3",
        "decoder_identity": decoder.identity,
        "analysis_address_space": getattr(decoder, "address_space", None),
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
            "byte_copy_bytes": MAX_BYTE_COPY,
            "indirect_symbol_work_per_module": MAX_INDIRECT_SYMBOL_WORK,
            "indirect_entry_cache_per_module": MAX_INDIRECT_ENTRIES,
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
