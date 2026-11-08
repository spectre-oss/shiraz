// SPDX-License-Identifier: GPL-3.0-only
#pragma once
namespace shiraz::guest {
// Nestable per-thread write interval shared by image relocation and closure
// code. No generated guest/JIT code may execute on the writing thread within a
// scope.
class JitWriteScope {
public:
  JitWriteScope();
  ~JitWriteScope();
  JitWriteScope(const JitWriteScope &) = delete;
  JitWriteScope &operator=(const JitWriteScope &) = delete;
};
} // namespace shiraz::guest
