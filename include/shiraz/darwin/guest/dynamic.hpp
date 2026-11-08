// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <mutex>

namespace shiraz::guest {
// The owner must share its serialization lock with all other Loader access.
// get_loader must return nullptr after the Loader lifetime; closures themselves
// are process-resident. refresh_code_validation publishes newly linked code
// before constructors can create threads or register callbacks.
struct DynamicHooks {
  std::function<Loader *()> get_loader;
  std::shared_ptr<std::recursive_mutex> mutex;
  std::function<void()> refresh_code_validation;
};
// Adds a real libdl.so guest namespace provider. Logical dlclose releases the
// handle; images stay resident under Loader's documented lifetime contract.
// RTLD_NEXT needs caller provenance and is explicitly refused for now.
Providers dynamic_personality(Providers providers, DynamicHooks hooks);
} // namespace shiraz::guest
