# SPDX-License-Identifier: GPL-3.0-only
"""Bounded scalar precision loss for otherwise identical abstract states.

The caller must group ONLY states with identical non-scalar facts, including
pointer/stack/return/lifetime facts and control location. It must keep previously
queued states and call observe only for newly admitted exact variants. This
helper does not compare or merge pointers, authenticate metadata, or establish
any guest/runtime behavior. It can only forget scalar facts.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class ScalarState:
    constants: tuple[tuple[str, int], ...]
    zero_upper: frozenset[str]
    widened: bool
    dropped_constants: frozenset[str]
    dropped_zero_upper: frozenset[str]


@dataclass(frozen=True)
class _Group:
    constants: tuple[tuple[str, int], ...]
    zero_upper: frozenset[str]
    variants: int


class ScalarWidening:
    """Retain initial variants, then use the intersection of every observation.

    Before max_variants observations, the incoming facts are returned intact.
    At the threshold and thereafter, only facts true in EVERY observation of
    that group survive. Missing constants mean unknown and cannot reappear in
    the intersection. The observation counter saturates at the threshold.

    max_groups bounds retained groups; input fact sizes and hashable group keys
    are bounded by the caller. Capacity exhaustion rejects only NEW groups;
    existing groups can still lose precision. No input or returned container is
    retained by reference.
    """

    def __init__(self, max_groups, max_variants=4):
        if type(max_groups) is not int or type(max_variants) is not int:
            raise TypeError("scalar widening bounds must be integers")
        if max_groups < 0 or max_variants < 1:
            raise ValueError("invalid scalar widening bounds")
        self.max_groups = max_groups
        self.max_variants = max_variants
        self._groups = {}

    def observe(self, group_key, constants, zero_upper):
        hash(group_key)  # Reject unhashable keys before changing retained state.
        if (not isinstance(constants, dict)
                or any(not isinstance(name, str) or type(value) is not int
                       for name, value in constants.items())
                or not isinstance(zero_upper, (set, frozenset))
                or any(not isinstance(name, str) for name in zero_upper)):
            raise TypeError("invalid scalar facts")
        incoming = tuple(sorted(constants.items()))
        upper = frozenset(zero_upper)
        previous = self._groups.get(group_key)
        if previous is None:
            if len(self._groups) >= self.max_groups:
                return None
            merged = _Group(incoming, upper, 1)
        else:
            shared = tuple((name, value) for name, value in previous.constants
                           if name in constants and constants[name] == value)
            merged = _Group(shared, previous.zero_upper & upper,
                            min(previous.variants + 1, self.max_variants))
        self._groups[group_key] = merged
        widened = merged.variants >= self.max_variants
        selected = merged.constants if widened else incoming
        selected_upper = merged.zero_upper if widened else upper
        return ScalarState(
            selected, selected_upper, widened,
            frozenset(constants) - frozenset(name for name, _ in selected),
            upper - selected_upper,
        )
