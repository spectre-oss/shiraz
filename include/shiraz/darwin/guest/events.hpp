// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <sys/types.h>
namespace shiraz::guest {
inline constexpr int event_descriptor_bound = 65536;
// eventfd/poll are real descriptor adapters. inotify currently reports ENOSYS
// explicitly, allowing callers such as Qt to choose their real polling backend.
// ppoll uses atomic native pselect; unsupported masks/high descriptors/bands and
// POLLRDHUP are rejected rather than represented by false readiness.
// Inherited virtual descriptors across fork/exec are unsupported: a fork child
// refuses counter/IO operations before touching copied native mutexes.
std::vector<ProviderExport> event_exports();
// REQUIRED io.cpp hooks: ordinary descriptors pass directly to real host IO.
// Use these for read/__read_chk, write, close, dup and dup2. Consult the Capture
// dispatchers before FileState's fcntl/ioctl parsing. This prevents exposing a
// readiness pipe as if it were the Linux counter descriptor itself.
// Ordinary IO uses lock-free descriptor hints before any registry/TLS lookup.
// Tracked descriptor operations inside guest signal handlers fail ENOTSUP.
bool event_descriptor_tracked(int fd) noexcept;
ssize_t event_descriptor_read(int fd, void *buffer, size_t count);
ssize_t event_descriptor_write(int fd, const void *buffer, size_t count);
int event_descriptor_close(int fd);
int event_descriptor_dup(int fd);
int event_descriptor_dup2(int source, int destination);
// Native atomic dup3 on macOS27+, otherwise ENOSYS. No dup2+fcntl fallback.
int event_descriptor_dup3(int source, int destination, int guest_flags);
bool events_fcntl(Capture &capture, int64_t &result);
bool events_ioctl(Capture &capture, int &result);
// Call at process/runtime shutdown after guest threads have stopped. The
// descriptor registry is process-wide because actual host descriptor numbers
// are shared with the rest of the runtime, not private emulated integers.
void event_descriptors_shutdown();
} // namespace shiraz::guest
