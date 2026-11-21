// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad.hpp"
#include "shiraz/darwin/guest/quad_pow.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include <cfenv>
#include <cstring>
namespace shiraz::guest {
ProviderExport quad_power_export() {
    auto* entry = closure([](Capture& capture) {
        QuadPowerBits base{}, exponent{};
        std::memcpy(&base, capture.frame.vectors[0], sizeof(base));
        std::memcpy(&exponent, capture.frame.vectors[1], sizeof(exponent));
        HostErrno preserve;
        QuadPowerResult value{{0, UINT64_C(0x7fff800000000000)}, ENOTSUP, 0};
        // The private MPFR runtime has thread-local state, but its allocator and
        // caches are not async-signal-safe. Never enter it from a guest handler.
        if (!in_guest_signal_handler())
            value = quad_power(base, exponent, std::fegetround());
        // Pinned Android bionic advertises MATH_ERREXCEPT, and its arm64 powl
        // implements domain/range cases via floating-point operations without
        // setting errno. Preserve guest errno for those mathematical outcomes;
        // retain explicit errors for failures of this experimental provider.
        if (value.error && value.error != EDOM && value.error != ERANGE)
            *guest_errno_location() = errno_to_guest(value.error);
        std::memcpy(capture.frame.result_vectors[0], &value.bits, sizeof(value.bits));
    });
    return {"powl",
            "LIBC",
            reinterpret_cast<uintptr_t>(entry),
            SymbolKind::Function,
            "quad/mpfr-binary128/q0-q1",
            false};
}
} // namespace shiraz::guest
