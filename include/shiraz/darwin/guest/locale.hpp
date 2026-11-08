// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <ctime>
namespace shiraz::guest {
// Android starts in C.UTF-8. uselocale selects a per-thread override; otherwise
// setlocale's guest-global choice applies. Neither changes the host locale.
bool current_locale_utf8();
// Validate an opaque locale_t, including Android LC_GLOBAL_LOCALE (-1).
// Null is not a valid explicit _l locale. Failure sets host errno to EINVAL.
bool locale_is_utf8(const void *handle, bool &result);
// Null here selects current guest locale for the non-_l time adapter. Explicit
// strftime_l must reject null before calling this internal helper. The native
// locale caches remain alive throughout the call and never escape to guests.
size_t locale_strftime(char *output, size_t size, const char *format,
                       const std::tm &time, const void *guest_locale);
std::vector<ProviderExport> locale_exports();
}
