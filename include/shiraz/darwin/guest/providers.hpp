// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <functional>
#include <tuple>

namespace shiraz::guest {
int *guest_errno_location();
int errno_to_guest(int host);
enum class ErrnoPolicy { None, Observe, Null, MinusOne };
class HostErrno {
public:
  HostErrno() : previous_(errno) { errno = 0; }
  ~HostErrno() { errno = previous_; }
  HostErrno(const HostErrno &) = delete;
  HostErrno &operator=(const HostErrno &) = delete;

private:
  int previous_;
};
// Only explicitly audited fixed signatures belong here. Structs, callbacks,
// variadic APIs, long double and layout-dependent handles need separate
// adapters.
template <typename R, typename... Args>
ProviderExport fixed_export(std::string name, std::string version,
                            R (*function)(Args...),
                            ErrnoPolicy policy = ErrnoPolicy::None) {
  static_assert(!std::is_same_v<R, long double> &&
                (!std::is_same_v<Args, long double> && ...));
  if (!function)
    throw ElfError("null personality implementation");
  if ((policy == ErrnoPolicy::Null && !std::is_pointer_v<R>) ||
      (policy == ErrnoPolicy::MinusOne &&
       !(std::is_integral_v<R> && std::is_signed_v<R>)) ||
      (std::is_void_v<R> && policy != ErrnoPolicy::None))
    throw ElfError("personality errno policy does not match its result type");
  void *address = closure([function, policy](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> arguments{cursor.read<Args>()...};
    HostErrno preserve;
    if constexpr (std::is_void_v<R>) {
      if (policy != ErrnoPolicy::None)
        throw ElfError("void provider cannot have a result errno policy");
      std::apply(function, arguments);
    } else {
      R result = std::apply(function, arguments);
      int error = errno;
      bool failed = false;
      if constexpr (std::is_pointer_v<R>)
        failed = policy == ErrnoPolicy::Null && result == nullptr;
      else if constexpr (std::is_integral_v<R>)
        failed = policy == ErrnoPolicy::MinusOne && result == R(-1);
      if ((policy == ErrnoPolicy::Observe && error) || failed)
        *guest_errno_location() = errno_to_guest(error ? error : EIO);
      return_value(capture, result);
    }
  });
  const char *abi = policy == ErrnoPolicy::None      ? "fixed/no-errno"
                    : policy == ErrnoPolicy::Observe ? "fixed/errno-observed"
                    : policy == ErrnoPolicy::Null    ? "fixed/errno-null"
                                                     : "fixed/errno-minus-one";
  return {std::move(name),
          std::move(version),
          reinterpret_cast<uintptr_t>(address),
          SymbolKind::Function,
          abi,
          false};
}
// Only an explicitly audited void/no-argument provider may opt into this
// contract. A captured implementation retains its owned state in the closure.
// If its implementation unexpectedly returns, fail rather than
// exposing a normal continuation contradicted by the binding metadata.
inline ProviderExport explicit_noreturn_closure_export(std::string name, std::string version,
                                                       std::function<void()> function) {
  if (!function)
    throw ElfError("null nonreturning personality implementation");
  void *address = closure([function = std::move(function)](Capture &) {
    HostErrno preserve;
    function();
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL nonreturning provider returned\n");
    std::abort();
  });
  return {std::move(name), std::move(version), reinterpret_cast<uintptr_t>(address),
          SymbolKind::Function, "fixed/nonreturning-noargs", false, true};
}
inline ProviderExport explicit_noreturn_export(std::string name, std::string version,
                                               void (*function)()) {
  if (!function)
    throw ElfError("null nonreturning personality implementation");
  return explicit_noreturn_closure_export(std::move(name), std::move(version), function);
}
Providers core_personality();
} // namespace shiraz::guest
