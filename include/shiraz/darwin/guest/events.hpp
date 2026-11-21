// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <sys/types.h>
#include <sys/stat.h>
#include "shiraz/darwin/guest/maps_stream.hpp"
namespace shiraz::guest {
inline constexpr int event_descriptor_bound = 65536;
// eventfd/poll are real descriptor adapters. inotify currently reports ENOSYS
// explicitly, allowing callers such as Qt to choose their real polling backend.
// ppoll uses atomic native pselect; unsupported masks/high descriptors/bands and
// POLLRDHUP are rejected rather than represented by false readiness.
// Inherited virtual descriptors across fork/exec are unsupported: a fork child
// refuses counter/IO operations before touching copied native mutexes.
std::vector<ProviderExport> event_exports();
// Internal readiness operations use the same guest descriptor registry as
// epoll and close. The event layout is Linux AArch64's 16-byte epoll_event.
int event_epoll_create();
int event_epoll_control(int queue, int operation, int fd, const void* event);
int event_epoll_wait(int queue, void* events, int capacity, int timeout_ms);
int event_epoll_remove_data(int queue, uint64_t data);
// REQUIRED io.cpp hooks: ordinary descriptors pass directly to real host IO.
// Use these for read/__read_chk, write, close, dup and dup2. Consult the Capture
// dispatchers before FileState's fcntl/ioctl parsing. This prevents exposing a
// readiness pipe as if it were the Linux counter descriptor itself.
// Ordinary IO uses lock-free descriptor hints before any registry/TLS lookup.
// Tracked descriptor operations inside guest signal handlers fail ENOTSUP.
bool event_descriptor_tracked(int fd) noexcept;
ssize_t event_descriptor_read(int fd, void* buffer, size_t count);
ssize_t event_descriptor_write(int fd, const void* buffer, size_t count);
int event_descriptor_close(int fd);
int event_descriptor_dup(int fd);
int event_descriptor_dup2(int source, int destination);
// Native atomic dup3 on macOS27+, otherwise ENOSYS. No dup2+fcntl fallback.
int event_descriptor_dup3(int source, int destination, int guest_flags);
bool events_fcntl(Capture& capture, int64_t& result);
bool events_ioctl(Capture& capture, int& result);
// Three synthetic sysfs directory nodes. The shared descriptor registry owns
// every alias; callers must not pass these descriptors to host filesystem IO.
// Enumeration and unmodelled descendants remain explicitly unsupported.
int sysfs_directory_open(unsigned node, int guest_flags, std::shared_ptr<void>* lease = nullptr);
// Returns zero for an ordinary descriptor, a node ID for a sysfs directory,
// or -1 with errno when virtual descriptor lookup is unsupported.
int sysfs_directory_node(int fd);
// An owning directory stream retains the exact description, preventing fd
// reuse from making its later close affect an unrelated descriptor.
std::shared_ptr<void> sysfs_directory_lease(int fd);
int sysfs_directory_close_owned(int fd, const std::shared_ptr<void>& lease);
// Maps bytes come from a complete, bounded snapshot factory. This constructor
// does not install a /proc path or relax native loader admission.
int process_maps_open(MapsStream::Factory factory, int native_flags);
bool process_file_stat(int fd, struct stat& output, int& result);
bool process_file_reject(int fd, int error, int& result);
bool process_file_pread(int fd, void* buffer, size_t count, int64_t offset, ssize_t& result);
bool process_file_pwrite(int fd, ssize_t& result);
bool process_file_seek(int fd, int64_t offset, int origin, int64_t& result);
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
// Disposable diagnostic only. Caller must first verify the actual argument
// range contains only the intended app process name and zero padding.
int probe_process_arguments_open(int native_flags);
inline bool probe_process_arguments_pread(int fd, void* buffer, size_t count, int64_t offset,
                                          ssize_t& result) {
    return process_file_pread(fd, buffer, count, offset, result);
}
inline bool probe_process_arguments_pwrite(int fd, ssize_t& result) {
    return process_file_pwrite(fd, result);
}
inline bool probe_process_arguments_seek(int fd, int64_t offset, int origin, int64_t& result) {
    return process_file_seek(fd, offset, origin, result);
}
bool probe_process_arguments_fstat(int fd, int& result);
#endif
// Call at process/runtime shutdown after guest threads have stopped. The
// descriptor registry is process-wide because actual host descriptor numbers
// are shared with the rest of the runtime, not private emulated integers.
void event_descriptors_shutdown();
} // namespace shiraz::guest
