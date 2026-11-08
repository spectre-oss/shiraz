// SPDX-License-Identifier: GPL-3.0-only
// Link-only fixture helper declarations; runtime must bind the host test
// adapter.
long host_fail(void) { __builtin_trap(); }
void host_barrier(void) { __builtin_trap(); }
