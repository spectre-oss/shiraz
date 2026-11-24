// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
namespace shiraz::guest {
// Exact rational -> IEEE754binary128 conversion. Results go directly to q0;
// Darwin long double/double never participate. Supports all four rounding modes
// without changing host fenv. Input/digit/resource bounds fail with EOVERFLOW.
std::vector<ProviderExport> quad_exports();
// Rigorous adaptive fixed-point intervals; raw binary128 q0 in/out. Explicit
// EOVERFLOW/qNaN if the bounded refinement cannot prove unique rounding.
ProviderExport quad_log2_export();
// Self-contained IEEE-754 binary128 powl and sqrtl providers (raw q0/q1 -> q0).
ProviderExport quad_powl_export();
ProviderExport quad_sqrtl_export();
// Experimental binary128 powl provider; guest-signal execution is refused.
ProviderExport quad_power_export();
} // namespace shiraz::guest
