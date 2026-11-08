// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/jit.hpp"
#include <pthread.h>
namespace shiraz::guest {
namespace {
thread_local unsigned depth = 0;
}
JitWriteScope::JitWriteScope() {
  if (depth++ == 0)
    pthread_jit_write_protect_np(0);
}
JitWriteScope::~JitWriteScope() {
  if (--depth == 0)
    pthread_jit_write_protect_np(1);
}
} // namespace shiraz::guest
