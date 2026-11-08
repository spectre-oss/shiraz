// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <filesystem>
namespace shiraz::guest {
// Load only an explicitly configured absolute Darwin EGL shim. Its typed
// procedures become guest ABI thunks shared by EGL/GLES provider namespaces.
// No unknown procedure, callback API or unreviewed EGLAttrib API passes through.
std::vector<Provider> guest_graphics_providers(const std::filesystem::path &shim);
} // namespace shiraz::guest
