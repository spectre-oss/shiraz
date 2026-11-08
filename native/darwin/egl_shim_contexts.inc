// SPDX-License-Identifier: GPL-3.0-only
// Included by native/egl_shim.cpp on macOS only, inside its anonymous
// namespace, after the Draw table and the per-thread current state.
//
// Draw::framebuffers maps a context handle to the window framebuffer object
// the shim built in that context. A framebuffer object is a container object:
// it dies with its context and is never shared, so a destroyed context's
// entry is dead. Without this hook the entry stays for the life of the window,
// and a later context that received the same handle would bind the dead name
// (in ES that silently creates an empty framebuffer) and read back nothing.
// ANGLE chromium/8127 numbers contexts from a process-wide counter
// (src/libANGLE/State.cpp gIDCounter), so on macOS this drops dead entries
// rather than fixing an observed reuse; Linux has the same table keyed by
// Mesa context pointers, which malloc can reuse. Darwin-only so that the Linux
// shim stays byte-identical.
void darwinForgetContext(EGLContext context) {
    if (context == EGL_NO_CONTEXT) return;
    std::lock_guard<std::mutex> guard(drawsMutex);
    for (auto& entry : draws) entry.second->framebuffers.erase(context);
    darwinZeroCopyForgetContext(context);  // M4b window slots (egl_shim_iosurface.inc)
}
