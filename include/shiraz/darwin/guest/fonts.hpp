// SPDX-License-Identifier: GPL-3.0-only
#pragma once
namespace shiraz::guest {
// native_flags are already translated Darwin O_* flags. A recognized Android
// font path is always handled, even on failure: callers must never fall back
// to opening /system on the host. Other /system paths are explicitly refused.
// Ordinary unknown paths return -1/handled=false and
// leave errno unchanged. Successful descriptors belong to the caller.
int openGuestFont(const char *path, int native_flags, bool &handled) noexcept;
} // namespace shiraz::guest
